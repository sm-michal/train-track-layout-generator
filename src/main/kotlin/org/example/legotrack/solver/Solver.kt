package org.example.legotrack.solver

import org.example.legotrack.model.*
import kotlin.math.*

class Solver(
    val inventory: Map<String, Int>, // ID to count
    val maxSolutions: Int = 1,
    val tolerancePos: Double = 0.5,
    val toleranceAngle: Double = 1.0
) {
    data class LayoutFeatures(
        val longStraights: Int = 0,
        val longTurns45: Int = 0,
        val longTurns90: Int = 0,
        val longTurns180: Int = 0,
        val zigZags: Int = 0,
        val isLShape: Boolean = false,
        val isUShape: Boolean = false,
        val hasSiding: Boolean = false,
        val hasDeadEnd: Boolean = false,
        val hasMultiLoop: Boolean = false
    )

    private val globalFeatureUsage = mutableMapOf<String, Int>().withDefault { 0 }
    private val solutions = mutableListOf<List<PlacedPiece>>()
    private var deadEndSolutionCount = 0
    private val seenSolutionSequences = mutableSetOf<List<String>>()
    private val sidingCache = mutableMapOf<String, List<PlacedPiece>?>()
    private val startPose = Pose(0.0, 0.0, 0.0)

    // Map of ID to Definition(s). For curves, one ID might map to both Left and Right definitions.
    private val pieceOptions = mutableMapOf<String, List<TrackPieceDefinition>>()

    private val mirrorMap: Map<String, String> by lazy {
        pieceOptions.values.flatten().associate { it.id to it.mirrorId }
    }

    init {
        // Initialize piece options based on known library pieces
        inventory.keys.forEach { id ->
            when (id) {
                "straight" -> pieceOptions[id] = listOf(TrackLibrary.STRAIGHT)
                "curve_r40" -> pieceOptions[id] = listOf(TrackLibrary.CURVE_R40, TrackLibrary.CURVE_R40_RIGHT)
                "switch_left" -> pieceOptions[id] = listOf(
                    TrackLibrary.SWITCH_LEFT,
                    TrackLibrary.SWITCH_LEFT_REV_STRAIGHT,
                    TrackLibrary.SWITCH_LEFT_REV_BRANCH
                )
                "switch_right" -> pieceOptions[id] = listOf(
                    TrackLibrary.SWITCH_RIGHT,
                    TrackLibrary.SWITCH_RIGHT_REV_STRAIGHT,
                    TrackLibrary.SWITCH_RIGHT_REV_BRANCH
                )
                // Add more as needed
            }
        }
    }

    private val maxPieceLength: Double = 32.0 // Switch straight is 32
    private val maxPieceAngle: Double = 22.5 // Max angle of a single piece

    fun solve(): List<List<PlacedPiece>> {
        solutions.clear()
        deadEndSolutionCount = 0
        seenSolutionSequences.clear()
        sidingCache.clear()
        globalFeatureUsage.clear()
        val currentInventory = inventory.toMutableMap()
        backtrack(startPose, currentInventory, mutableListOf())
        return solutions
    }

    private fun extractFeatures(path: List<PlacedPiece>): LayoutFeatures {
        var longStraights = 0
        var currentStraight = 0

        var longTurns45 = 0
        var longTurns90 = 0
        var longTurns180 = 0
        var currentTurnAngle = 0.0

        var zigZags = 0
        var lastTurnDir = 0 // 1 for right, -1 for left, 0 for none

        // Pattern detection for L/U shapes
        val turns = mutableListOf<Double>() // List of continuous turn angles

        for (piece in path) {
            val type = piece.definition.type
            val angle = piece.definition.exits.getOrNull(piece.chosenExitIndex)?.dRotation ?: 0.0

            // Straights
            if (type == TrackType.STRAIGHT || (type == TrackType.SWITCH && abs(angle) < 0.1)) {
                currentStraight += if (type == TrackType.SWITCH) 2 else 1

                if (abs(currentTurnAngle) > 0.1) {
                    turns.add(currentTurnAngle)
                    val absA = abs(currentTurnAngle)
                    if (abs(absA - 45.0) < 1.0) longTurns45++
                    else if (abs(absA - 90.0) < 1.0) longTurns90++
                    else if (abs(absA - 180.0) < 1.0) longTurns180++
                    currentTurnAngle = 0.0
                }
            } else if (type == TrackType.CURVE || (type == TrackType.SWITCH && abs(angle) > 0.1)) {
                if (currentStraight >= 4) {
                    longStraights++
                }
                currentStraight = 0

                val dir = if (angle > 0) 1 else -1
                if (lastTurnDir != 0 && lastTurnDir != dir) {
                    zigZags++
                    turns.add(currentTurnAngle)
                    currentTurnAngle = 0.0
                }
                currentTurnAngle += angle
                lastTurnDir = dir
            }
        }
        // Final pieces
        if (currentStraight >= 4) longStraights++
        if (abs(currentTurnAngle) > 0.1) {
            turns.add(currentTurnAngle)
            val absA = abs(currentTurnAngle)
            if (abs(absA - 45.0) < 1.0) longTurns45++
            else if (abs(absA - 90.0) < 1.0) longTurns90++
            else if (abs(absA - 180.0) < 1.0) longTurns180++
        }

        // L-shape and U-shape detection based on turn directionality and total rotation
        // A simple loop has sum of turns = 360 and absSum = 360.
        // A loop with 1 inward corner (L-shape) has sum = 360 and absSum = 540 (ratio 1.5)
        // A loop with 2 inward corners (U-shape) has sum = 360 and absSum = 720 (ratio 2.0)

        var isLShape = false
        var isUShape = false

        val totalRotation = turns.sum()
        val totalAbsRotation = turns.sumOf { abs(it) }

        if (abs(totalRotation) > 1.0) { // Avoid division by zero
            val ratio = totalAbsRotation / abs(totalRotation)
            // Use a bit of tolerance for the ratio
            if (abs(ratio - 1.5) < 0.1) isLShape = true
            if (abs(ratio - 2.0) < 0.1) isUShape = true
        }

        val hasSiding = path.any { it.definition.type == TrackType.SWITCH && !it.isDeadEnd && it.deadEndExits.isEmpty() }
        val hasDeadEnd = path.any { it.isDeadEnd || it.deadEndExits.isNotEmpty() }

        return LayoutFeatures(
            longStraights = longStraights,
            longTurns45 = longTurns45,
            longTurns90 = longTurns90,
            longTurns180 = longTurns180,
            zigZags = zigZags,
            isLShape = isLShape,
            isUShape = isUShape,
            hasSiding = hasSiding,
            hasDeadEnd = hasDeadEnd,
            hasMultiLoop = hasSiding // Simplified for now
        )
    }

    private fun calculateScore(features: LayoutFeatures): Double {
        var score = 0.0

        // Rewards (prioritized weights)
        score += (if (features.hasMultiLoop) 1.0 else 0.0) * 1000.0
        score += (if (features.hasSiding) 1.0 else 0.0) * 500.0
        score += features.longStraights.toDouble() * 100.0
        score += features.longTurns180.toDouble() * 80.0
        score += features.longTurns90.toDouble() * 80.0
        score += features.longTurns45.toDouble() * 80.0
        score += (if (features.isUShape) 1.0 else 0.0) * 50.0
        score += (if (features.isLShape) 1.0 else 0.0) * 30.0
        score += (if (features.hasDeadEnd) 1.0 else 0.0) * 10.0

        // Penalties
        score -= features.zigZags.toDouble() * 50.0

        // Diversity (Strategy B)
        if (features.isLShape) score -= globalFeatureUsage.getOrDefault("l_shape", 0) * 200.0
        if (features.isUShape) score -= globalFeatureUsage.getOrDefault("u_shape", 0) * 300.0
        if (features.hasSiding) score -= globalFeatureUsage.getOrDefault("siding", 0) * 400.0

        return score
    }

    data class IncrementalFeatures(
        val totalRotation: Double = 0.0,
        val totalAbsRotation: Double = 0.0,
        val currentStraight: Int = 0,
        val currentTurnAngle: Double = 0.0,
        val lastTurnDir: Int = 0,
        val longStraights: Int = 0,
        val longTurns45: Int = 0,
        val longTurns90: Int = 0,
        val longTurns180: Int = 0,
        val zigZags: Int = 0,
        val switchCount: Int = 0
    )

    private fun updateIncremental(feat: IncrementalFeatures, piece: PlacedPiece): IncrementalFeatures {
        var tr = feat.totalRotation
        var tar = feat.totalAbsRotation
        var cs = feat.currentStraight
        var cta = feat.currentTurnAngle
        var ltd = feat.lastTurnDir
        var ls = feat.longStraights
        var lt45 = feat.longTurns45
        var lt90 = feat.longTurns90
        var lt180 = feat.longTurns180
        var zz = feat.zigZags
        var swc = feat.switchCount + if (piece.definition.type == TrackType.SWITCH) 1 else 0

        val type = piece.definition.type
        val angle = piece.definition.exits.getOrNull(piece.chosenExitIndex)?.dRotation ?: 0.0

        if (type == TrackType.STRAIGHT || (type == TrackType.SWITCH && abs(angle) < 0.1)) {
            cs += if (type == TrackType.SWITCH) 2 else 1
            if (abs(cta) > 0.1) {
                tr += cta
                tar += abs(cta)
                val absA = abs(cta)
                if (abs(absA - 45.0) < 1.0) lt45++
                else if (abs(absA - 90.0) < 1.0) lt90++
                else if (abs(absA - 180.0) < 1.0) lt180++
                cta = 0.0
            }
        } else if (type == TrackType.CURVE || (type == TrackType.SWITCH && abs(angle) > 0.1)) {
            if (cs >= 4) ls++
            cs = 0
            val dir = if (angle > 0) 1 else -1
            if (ltd != 0 && ltd != dir) {
                zz++
                tr += cta
                tar += abs(cta)
                cta = 0.0
            }
            cta += angle
            ltd = dir
        }

        return IncrementalFeatures(tr, tar, cs, cta, ltd, ls, lt45, lt90, lt180, zz, swc)
    }

    private fun IncrementalFeatures.toLayoutFeatures(): LayoutFeatures {
        val finalTr = totalRotation + currentTurnAngle
        val finalTar = totalAbsRotation + abs(currentTurnAngle)
        val ratio = if (abs(finalTr) > 1.0) finalTar / abs(finalTr) else 1.0
        return LayoutFeatures(
            longStraights = longStraights,
            longTurns45 = longTurns45,
            longTurns90 = longTurns90,
            longTurns180 = longTurns180,
            zigZags = zigZags,
            isLShape = abs(ratio - 1.5) < 0.1,
            isUShape = abs(ratio - 2.0) < 0.1,
            hasSiding = switchCount > 0,
            hasDeadEnd = false, // Hard to detect incrementally without full path
            hasMultiLoop = switchCount >= 2
        )
    }

    private fun backtrack(
        currentPose: Pose,
        remaining: MutableMap<String, Int>,
        path: MutableList<PlacedPiece>,
        incFeat: IncrementalFeatures = IncrementalFeatures()
    ) {
        if (solutions.size >= maxSolutions) return

        // Check for closure
        if (path.size > 3) { // At least 4 pieces for a non-trivial loop
            val dist = currentPose.distanceTo(startPose)
            val angleDist = currentPose.angleDistanceTo(startPose)
            if (dist < tolerancePos && angleDist < toleranceAngle) {
                val unusedExits = mutableListOf<Pair<Int, PlacedPiece>>()
                for (piece in path) {
                    if (piece.definition.type == TrackType.SWITCH) {
                        val entryPose = piece.pose
                        val chosenExitPose = piece.exitPose
                        piece.allConnectorPoses.forEachIndexed { idx, p ->
                            if (p.distanceTo(entryPose) > 0.1 && p.distanceTo(chosenExitPose) > 0.1) {
                                unusedExits.add(idx to piece)
                            }
                        }
                    }
                }

                resolveUnusedExits(unusedExits, remaining, path)
                return
            }
        }

        // Pruning
        val distToStart = currentPose.distanceTo(startPose)
        val remainingCount = remaining.values.sum()
        if (distToStart > remainingCount * maxPieceLength + tolerancePos) {
            return
        }

        val angleToStart = currentPose.angleDistanceTo(startPose)
        // Each piece can change angle by at most 22.5 degrees
        if (angleToStart > remainingCount * maxPieceAngle + toleranceAngle) {
            return
        }

        val candidates = mutableListOf<PlacedPiece>()
        for (id in remaining.keys) {
            val count = remaining[id]!!
            if (count > 0) {
                val options = pieceOptions[id] ?: continue
                for (pieceDef in options) {
                    for (exitIndex in pieceDef.exits.indices) {
                        candidates.add(PlacedPiece(pieceDef, currentPose, exitIndex))
                    }
                }
            }
        }

        // Heuristic: sort candidates by how much they improve distance/angle to start + layout score
        val candidateScores = candidates.associateWith { candidate ->
            val nextPose = candidate.exitPose
            val distToStartSq = nextPose.distanceSq(startPose)
            val angleToStartWeight = nextPose.angleDistanceTo(startPose).pow(2) * 5.0

            val geoHeuristic = -(distToStartSq + angleToStartWeight)

            val nextFeat = updateIncremental(incFeat, candidate)
            val layoutScore = calculateScore(nextFeat.toLayoutFeatures())

            geoHeuristic + layoutScore
        }
        candidates.sortByDescending { candidateScores[it] }

        for (nextPiece in candidates) {
            val fullId = nextPiece.definition.id
            val inventoryId = getInventoryId(fullId)

            val count = remaining[inventoryId] ?: 0
            if (count <= 0) continue

            if (isCollision(nextPiece, path)) continue

            remaining[inventoryId] = count - 1
            path.add(nextPiece)

            backtrack(nextPiece.exitPose, remaining, path, updateIncremental(incFeat, nextPiece))

            path.removeAt(path.size - 1)
            remaining[inventoryId] = count

            if (solutions.size >= maxSolutions) return
        }
    }

    private fun getInventoryId(fullId: String): String {
        return when {
            fullId.contains("switch_left") -> "switch_left"
            fullId.contains("switch_right") -> "switch_right"
            fullId.contains("curve_r40") -> "curve_r40"
            else -> fullId
        }
    }

    private fun getPathSequence(path: List<PlacedPiece>): List<String> {
        return path.map { piece ->
            val deadEnds = piece.deadEndExits.joinToString(",")
            "${piece.definition.id}:${piece.chosenExitIndex}:${piece.isDeadEnd}:${deadEnds}"
        }
    }

    private fun updateGlobalUsage(path: List<PlacedPiece>) {
        val features = extractFeatures(path)
        if (features.isLShape) globalFeatureUsage["l_shape"] = globalFeatureUsage.getValue("l_shape") + 1
        if (features.isUShape) globalFeatureUsage["u_shape"] = globalFeatureUsage.getValue("u_shape") + 1
        if (features.hasSiding) globalFeatureUsage["siding"] = globalFeatureUsage.getValue("siding") + 1
    }

    private fun resolveUnusedExits(
        unusedExits: List<Pair<Int, PlacedPiece>>,
        remaining: MutableMap<String, Int>,
        currentPath: List<PlacedPiece>
    ) {
        if (unusedExits.isEmpty()) {
            val sequence = getPathSequence(currentPath)
            val hasDeadEnds = currentPath.any { it.isDeadEnd || it.deadEndExits.isNotEmpty() }
            val canonical = getCanonicalSequence(sequence, isCycle = !hasDeadEnds)
            if (seenSolutionSequences.add(canonical)) {
                solutions.add(currentPath.toList())
                updateGlobalUsage(currentPath)
            }
            return
        }

        // Try connecting the first unused exit to any other unused exit
        val (idx1, s1) = unusedExits[0]
        val pose1 = s1.allConnectorPoses[idx1]

        for (i in 1 until unusedExits.size) {
            val (idx2, s2) = unusedExits[i]
            val pose2 = s2.allConnectorPoses[idx2]
            val targetPose = pose2.copy(rotation = (pose2.rotation + 180.0) % 360.0)

            val sidingPath = mutableListOf<PlacedPiece>()
            val tempPath = currentPath.toMutableList()

            if (findPathBetweenBacktrack(pose1, targetPose, remaining, tempPath, sidingPath, 6)) {
                val nextUnused = unusedExits.filterIndexed { index, _ -> index != 0 && index != i }
                resolveUnusedExits(nextUnused, remaining, tempPath)

                // Backtrack inventory manually
                for (p in sidingPath) {
                    val invId = getInventoryId(p.definition.id)
                    remaining[invId] = (remaining[invId] ?: 0) + 1
                }
            }
        }

        // Try making the first unused exit a dead end (if allowed)
        val switchCount = currentPath.count { it.definition.type == TrackType.SWITCH }
        if (switchCount % 2 != 0 && deadEndSolutionCount < 1) {
            val (exitIdx, switch) = unusedExits[0]
            val startPose = switch.allConnectorPoses[exitIdx]
            val currentBranch = mutableListOf<PlacedPiece>()
            searchBranchRecursive(switch, exitIdx, startPose, remaining, currentPath, currentBranch, 0, unusedExits.drop(1))
        }
    }

    private fun searchBranchRecursive(
        switch: PlacedPiece,
        exitIdx: Int,
        currentPose: Pose,
        remaining: MutableMap<String, Int>,
        mainPath: List<PlacedPiece>,
        currentBranch: MutableList<PlacedPiece>,
        depth: Int,
        otherUnused: List<Pair<Int, PlacedPiece>>
    ) {
        if (depth <= 4) {
            val pathWithDeadEnd = if (currentBranch.isEmpty()) {
                mainPath.map {
                    if (it === switch) it.copy(deadEndExits = it.deadEndExits + exitIdx)
                    else it
                }
            } else {
                val lastPiece = currentBranch.last()
                val updatedLastPiece = lastPiece.copy(isDeadEnd = true)
                mainPath.toList() + currentBranch.dropLast(1) + updatedLastPiece
            }

            if (otherUnused.isEmpty()) {
                val sequence = getPathSequence(pathWithDeadEnd)
                val canonical = getCanonicalSequence(sequence, isCycle = false)
                if (seenSolutionSequences.add(canonical)) {
                    solutions.add(pathWithDeadEnd)
                    updateGlobalUsage(pathWithDeadEnd)
                    deadEndSolutionCount++
                }
            } else {
                resolveUnusedExits(otherUnused, remaining, pathWithDeadEnd)
            }
        }

        if (depth >= 4) return

        for (id in remaining.keys) {
            if (id.contains("switch")) continue
            val count = remaining[id]!!
            if (count > 0) {
                val options = pieceOptions[id] ?: continue
                for (pieceDef in options) {
                    for (exitIndex in pieceDef.exits.indices) {
                        val nextPiece = PlacedPiece(pieceDef, currentPose, exitIndex)
                        if (isCollision(nextPiece, mainPath + currentBranch)) continue

                        remaining[id] = count - 1
                        currentBranch.add(nextPiece)
                        searchBranchRecursive(switch, exitIdx, nextPiece.exitPose, remaining, mainPath, currentBranch, depth + 1, otherUnused)
                        currentBranch.removeAt(currentBranch.size - 1)
                        remaining[id] = count
                    }
                }
            }
        }
    }

    private fun findPathBetweenBacktrack(
        currentPose: Pose,
        targetPose: Pose,
        remaining: MutableMap<String, Int>,
        path: MutableList<PlacedPiece>,
        sidingPath: MutableList<PlacedPiece>,
        maxDepth: Int
    ): Boolean {
        val relX = targetPose.x - currentPose.x
        val relY = targetPose.y - currentPose.y
        val rad = Math.toRadians(-currentPose.rotation)
        val c = cos(rad)
        val s = sin(rad)
        val localX = relX * c - relY * s
        val localY = relX * s + relY * c
        val relRot = (targetPose.rotation - currentPose.rotation + 360.0) % 360.0
        val cacheKey = "${(localX / 0.1).toInt()}:${(localY / 0.1).toInt()}:${(relRot / 0.1).toInt()}:$maxDepth"

        if (sidingCache.containsKey(cacheKey) && sidingCache[cacheKey] == null) return false

        val distToTarget = currentPose.distanceTo(targetPose)
        if (distToTarget < tolerancePos &&
            currentPose.angleDistanceTo(targetPose) < toleranceAngle) {
            return true
        }
        if (maxDepth <= 0) return false

        // Pruning
        if (distToTarget > maxDepth * 32.0 + tolerancePos) return false
        val angleToTarget = currentPose.angleDistanceTo(targetPose)
        if (angleToTarget > maxDepth * 22.5 + toleranceAngle) return false

        val candidates = mutableListOf<PlacedPiece>()
        for (id in remaining.keys) {
            if (id.contains("switch")) continue
            val count = remaining[id]!!
            if (count > 0) {
                val options = pieceOptions[id] ?: continue
                for (pieceDef in options) {
                    for (exitIndex in pieceDef.exits.indices) {
                        candidates.add(PlacedPiece(pieceDef, currentPose, exitIndex))
                    }
                }
            }
        }

        candidates.sortBy { candidate ->
            val nextPose = candidate.exitPose
            nextPose.distanceSq(targetPose) + nextPose.angleDistanceTo(targetPose).pow(2) * 5.0
        }

        for (nextPiece in candidates) {
            val fullId = nextPiece.definition.id
            val inventoryId = getInventoryId(fullId)

            val count = remaining[inventoryId] ?: 0
            if (count <= 0) continue

            if (isCollision(nextPiece, path)) continue

            remaining[inventoryId] = count - 1
            path.add(nextPiece)
            sidingPath.add(nextPiece)

            if (findPathBetweenBacktrack(nextPiece.exitPose, targetPose, remaining, path, sidingPath, maxDepth - 1)) {
                return true
            }

            sidingPath.removeAt(sidingPath.size - 1)
            path.removeAt(path.size - 1)
            remaining[inventoryId] = count
        }
        sidingCache[cacheKey] = null
        return false
    }

    private fun isCollision(newPiece: PlacedPiece, path: List<PlacedPiece>): Boolean {
        val newPoints = newPiece.checkpointPoses
        val COLLISION_DIST_SQ = 7.0 * 7.0

        // Against path
        for (oldPiece in path) {
            val oldPoints = oldPiece.checkpointPoses
            for (np in newPoints) {
                for (op in oldPoints) {
                    if (np.distanceSq(op) < COLLISION_DIST_SQ) return true
                }
            }
        }

        // Against start
        if (path.size > 2) {
            val firstPiece = path.firstOrNull()
            if (firstPiece != null) {
                val startPoints = firstPiece.checkpointPoses
                for (np in newPoints) {
                    for (sp in startPoints) {
                        if (np.distanceSq(sp) < COLLISION_DIST_SQ) {
                            if (newPiece.exitPose.distanceSq(startPose) > (tolerancePos * 10).pow(2)) return true
                        }
                    }
                }
            }
        }

        return false
    }

    private fun getCanonicalSequence(sequence: List<String>, isCycle: Boolean = true): List<String> {
        return getCanonicalSequence(sequence, mirrorMap, isCycle)
    }

    companion object {
        fun getCanonicalSequence(sequence: List<String>, mirrorMap: Map<String, String>, isCycle: Boolean = true): List<String> {
            val s = sequence
            val m = s.map { str ->
                val parts = str.split(":")
                val id = parts[0]
                val mirroredId = mirrorMap[id] ?: id
                (listOf(mirroredId) + parts.drop(1)).joinToString(":")
            }
            val r = s.reversed()
            val mr = m.reversed()

            val equivalents = listOf(s, m, r, mr)

            val allRepresentations = if (isCycle) {
                equivalents.flatMap { eq ->
                    (eq.indices).map { i ->
                        eq.drop(i) + eq.take(i)
                    }
                }
            } else {
                equivalents
            }

            return allRepresentations.minWithOrNull(object : Comparator<List<String>> {
                override fun compare(o1: List<String>, o2: List<String>): Int {
                    for (i in o1.indices) {
                        val cmp = o1[i].compareTo(o2[i])
                        if (cmp != 0) return cmp
                    }
                    return 0
                }
            }) ?: s
        }
    }
}
