package org.example.legotrack.solver

import org.example.legotrack.model.*
import kotlin.math.*

class Solver(
    val inventory: Map<String, Int>, // ID to count
    val maxSolutions: Int = 1,
    val tolerancePos: Double = 0.5,
    val toleranceAngle: Double = 1.0
) {
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
        val currentInventory = inventory.toMutableMap()
        backtrack(startPose, currentInventory, mutableListOf())
        return solutions
    }

    private fun backtrack(
        currentPose: Pose,
        remaining: MutableMap<String, Int>,
        path: MutableList<PlacedPiece>
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

        // Heuristic: sort candidates by how much they improve distance/angle to start
        candidates.sortBy { candidate ->
            val nextPose = candidate.exitPose
            val distScore = nextPose.distanceSq(startPose)
            val angleScore = nextPose.angleDistanceTo(startPose).pow(2) * 5.0
            // Boost switches to ensure they are used
            val switchBoost = if (candidate.definition.type == TrackType.SWITCH) -500.0 else 0.0
            distScore + angleScore + switchBoost
        }

        for (nextPiece in candidates) {
            val fullId = nextPiece.definition.id
            val inventoryId = when {
                fullId.contains("switch_left") -> "switch_left"
                fullId.contains("switch_right") -> "switch_right"
                else -> fullId
            }

            val count = remaining[inventoryId] ?: 0
            if (count <= 0) continue

            if (isCollision(nextPiece, path)) continue

            remaining[inventoryId] = count - 1
            path.add(nextPiece)

            backtrack(nextPiece.exitPose, remaining, path)

            path.removeAt(path.size - 1)
            remaining[inventoryId] = count

            if (solutions.size >= maxSolutions) return
        }
    }

    private fun getPathSequence(path: List<PlacedPiece>): List<String> {
        return path.map { piece ->
            val deadEnds = piece.deadEndExits.joinToString(",")
            "${piece.definition.id}:${piece.chosenExitIndex}:${piece.isDeadEnd}:${deadEnds}"
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

                // Backtrack inventory manually because findPathBetweenBacktrack now won't restore on success
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

        // Pruning: can't reach target even with max length pieces
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
            val inventoryId = when {
                fullId.contains("switch_left") -> "switch_left"
                fullId.contains("switch_right") -> "switch_right"
                fullId.contains("curve_r40") -> "curve_r40"
                else -> fullId
            }

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


    private fun tryAddDeadEnd(
        switch: PlacedPiece,
        exitIdx: Int,
        remaining: MutableMap<String, Int>,
        mainPath: List<PlacedPiece>
    ) {
        val startPose = switch.allConnectorPoses[exitIdx]
        val currentBranch = mutableListOf<PlacedPiece>()
        searchBranch(switch, exitIdx, startPose, remaining, mainPath, currentBranch, 0)
    }

    private fun searchBranch(
        switch: PlacedPiece,
        exitIdx: Int,
        currentPose: Pose,
        remaining: MutableMap<String, Int>,
        mainPath: List<PlacedPiece>,
        currentBranch: MutableList<PlacedPiece>,
        depth: Int
    ) {
        if (depth <= 4) {
            val pathWithDeadEnd = if (currentBranch.isEmpty()) {
                mainPath.map {
                    if (it === switch) it.copy(deadEndExits = listOf(exitIdx))
                    else it
                }
            } else {
                val lastPiece = currentBranch.last()
                val updatedLastPiece = lastPiece.copy(isDeadEnd = true)
                mainPath.toList() + currentBranch.dropLast(1) + updatedLastPiece
            }

            val sequence = getPathSequence(pathWithDeadEnd)
            val canonical = getCanonicalSequence(sequence, isCycle = false)
            if (seenSolutionSequences.add(canonical)) {
                solutions.add(pathWithDeadEnd)
                deadEndSolutionCount++
                return
            }
        }

        if (depth >= 4) return

        for (id in remaining.keys) {
            val count = remaining[id]!!
            if (count > 0) {
                val options = pieceOptions[id] ?: continue
                for (pieceDef in options) {
                    for (exitIndex in pieceDef.exits.indices) {
                        val nextPiece = PlacedPiece(pieceDef, currentPose, exitIndex)
                        if (isCollision(nextPiece, mainPath + currentBranch)) continue

                        remaining[id] = count - 1
                        currentBranch.add(nextPiece)
                        searchBranch(switch, exitIdx, nextPiece.exitPose, remaining, mainPath, currentBranch, depth + 1)
                        if (deadEndSolutionCount >= 1) return
                        currentBranch.removeAt(currentBranch.size - 1)
                        remaining[id] = count
                    }
                }
            }
        }
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
                            // If we are close to the start but NOT about to close the loop, it's a collision
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
