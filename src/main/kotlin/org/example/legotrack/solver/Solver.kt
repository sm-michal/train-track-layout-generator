package org.example.legotrack.solver

import kotlinx.coroutines.*
import org.example.legotrack.model.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

class Solver(
    val inventory: Map<String, Int>, // ID to count
    val maxSolutions: Int = 1,
    val tolerancePos: Double = 0.5,
    val toleranceAngle: Double = 1.0
) {
    private val globalFeatureUsage = mutableMapOf<String, Int>().withDefault { 0 }
    private val scorer = LayoutScorer(globalFeatureUsage)
    private val solutions = java.util.Collections.synchronizedList(mutableListOf<ScoredSolution>())
    private var deadEndSolutionCount = 0
    private val nodesExplored = AtomicLong(0)
    private val seenSolutionSequences = java.util.Collections.synchronizedSet(mutableSetOf<List<String>>())
    private val sidingCache = java.util.concurrent.ConcurrentHashMap<String, List<PlacedPiece>?>()
    private val startPose = Pose(0.0, 0.0, 0.0)

    class SpatialGrid(val cellSize: Double = 32.0) {
        private val grid = mutableMapOf<String, MutableList<Pose>>()

        private fun gridKey(x: Int, y: Int) = "$x,$y"

        private fun getCellCoords(pose: Pose): Pair<Int, Int> {
            return (pose.x / cellSize).toInt() to (pose.y / cellSize).toInt()
        }

        fun add(piece: PlacedPiece) {
            for (pose in piece.checkpointPoses) {
                val (x, y) = getCellCoords(pose)
                grid.getOrPut(gridKey(x, y)) { mutableListOf() }.add(pose)
            }
        }

        fun remove(piece: PlacedPiece) {
            for (pose in piece.checkpointPoses) {
                val (x, y) = getCellCoords(pose)
                grid[gridKey(x, y)]?.remove(pose)
            }
        }

        fun checkCollision(piece: PlacedPiece, rangeSq: Double): Boolean {
            for (pose in piece.checkpointPoses) {
                val (cx, cy) = getCellCoords(pose)
                for (x in cx - 1..cx + 1) {
                    for (y in cy - 1..cy + 1) {
                        val points = grid[gridKey(x, y)] ?: continue
                        for (p in points) {
                            if (pose.distanceSq(p) < rangeSq) return true
                        }
                    }
                }
            }
            return false
        }

        fun copy(): SpatialGrid {
            val newGrid = SpatialGrid(cellSize)
            grid.forEach { (k, v) -> newGrid.grid[k] = ArrayList(v) }
            return newGrid
        }
    }

    // Map of ID to Definition(s). For curves, one ID might map to both Left and Right definitions.
    private val pieceOptions = mutableMapOf<String, List<TrackPieceDefinition>>()

    private val mirrorMap: Map<String, String> by lazy {
        pieceOptions.values.flatten().associate { it.id to it.mirrorId }
    }
    private val canonicalizer by lazy { SolutionCanonicalizer(mirrorMap) }

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

    data class ScoredSolution(
        val path: List<PlacedPiece>,
        val scoreBreakdown: LayoutScorer.ScoreBreakdown
    )

    private val maxPieceLength: Double = 32.0 // Switch straight is 32
    private val maxPieceAngle: Double = 22.5 // Max angle of a single piece

    fun solve(): List<ScoredSolution> = runBlocking(Dispatchers.Default) {
        solutions.clear()
        deadEndSolutionCount = 0
        nodesExplored.set(0)
        seenSolutionSequences.clear()
        sidingCache.clear()
        globalFeatureUsage.clear()
        val currentInventory = inventory.toMutableMap()
        val grid = SpatialGrid()
        backtrackParallel(startPose, currentInventory, mutableListOf(), grid, 0, IncrementalFeatures())

        solutions.toList().sortedByDescending { it.scoreBreakdown.totalScore }
    }

    private suspend fun backtrackParallel(
        currentPose: Pose,
        remaining: MutableMap<String, Int>,
        path: MutableList<PlacedPiece>,
        grid: SpatialGrid,
        depth: Int,
        incFeat: IncrementalFeatures
    ) {
        if (solutions.size >= maxSolutions) return

        // Check for closure
        if (path.size > 3) {
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
                resolveUnusedExits(unusedExits, remaining, path, grid)
                return
            }
        }

        val candidates = getCandidates(currentPose, remaining, incFeat)

        if (depth < 2) {
            coroutineScope {
                candidates.forEach { nextPiece ->
                    launch {
                        if (solutions.size >= maxSolutions) return@launch
                        val inventoryId = getInventoryId(nextPiece.definition.id)
                        val count = remaining[inventoryId] ?: 0
                        if (count <= 0) return@launch
                        if (isCollision(nextPiece, path, grid)) return@launch

                        val currentNodes = nodesExplored.incrementAndGet()
                        if (currentNodes % 100000 == 0L) {
                            println("Explored $currentNodes nodes, found ${solutions.size} solutions...")
                        }

                        val nextRemaining = remaining.toMutableMap()
                        nextRemaining[inventoryId] = count - 1
                        val nextPath = path.toMutableList()
                        nextPath.add(nextPiece)
                        val nextGrid = grid.copy()
                        if (nextPath.size > 1) nextGrid.add(nextPiece)

                        backtrackParallel(nextPiece.exitPose, nextRemaining, nextPath, nextGrid, depth + 1, scorer.updateIncremental(incFeat, nextPiece))
                    }
                }
            }
        } else {
            backtrackSerial(currentPose, remaining, path, grid, incFeat)
        }
    }

    private fun backtrackSerial(
        currentPose: Pose,
        remaining: MutableMap<String, Int>,
        path: MutableList<PlacedPiece>,
        grid: SpatialGrid,
        incFeat: IncrementalFeatures
    ) {
        if (solutions.size >= maxSolutions) return

        // Check for closure
        if (path.size > 3) {
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
                resolveUnusedExitsSerial(unusedExits, remaining, path, grid)
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
        if (angleToStart > remainingCount * maxPieceAngle + toleranceAngle) {
            return
        }

        val candidates = getCandidates(currentPose, remaining, incFeat)

        for (nextPiece in candidates) {
            if (solutions.size >= maxSolutions) return

            val fullId = nextPiece.definition.id
            val inventoryId = getInventoryId(fullId)

            val count = remaining[inventoryId] ?: 0
            if (count <= 0) continue

            if (isCollision(nextPiece, path, grid)) continue

            val currentNodes = nodesExplored.incrementAndGet()
            if (currentNodes % 100000 == 0L) {
                println("Explored $currentNodes nodes, found ${solutions.size} solutions...")
            }

            remaining[inventoryId] = count - 1
            path.add(nextPiece)
            if (path.size > 1) grid.add(nextPiece)

            backtrackSerial(nextPiece.exitPose, remaining, path, grid, scorer.updateIncremental(incFeat, nextPiece))

            if (path.size > 1) grid.remove(nextPiece)
            path.removeAt(path.size - 1)
            remaining[inventoryId] = count
        }
    }

    private fun getCandidates(currentPose: Pose, remaining: Map<String, Int>, incFeat: IncrementalFeatures): List<PlacedPiece> {
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

            val nextFeat = scorer.updateIncremental(incFeat, candidate)
            val layoutScore = with(scorer) { calculateScore(nextFeat.toLayoutFeatures()) }

            geoHeuristic + layoutScore
        }
        candidates.sortByDescending { candidateScores[it] }
        return candidates
    }

    private fun getInventoryId(fullId: String): String {
        return when {
            fullId.contains("switch_left") -> "switch_left"
            fullId.contains("switch_right") -> "switch_right"
            fullId.contains("curve_r40") -> "curve_r40"
            else -> fullId
        }
    }

    private fun updateGlobalUsage(path: List<PlacedPiece>) {
        val features = scorer.extractFeatures(path)
        if (features.isLShape) globalFeatureUsage["l_shape"] = globalFeatureUsage.getOrDefault("l_shape", 0) + 1
        if (features.isUShape) globalFeatureUsage["u_shape"] = globalFeatureUsage.getOrDefault("u_shape", 0) + 1
        if (features.hasSiding) globalFeatureUsage["siding"] = globalFeatureUsage.getOrDefault("siding", 0) + 1
    }

    private fun resolveUnusedExits(
        unusedExits: List<Pair<Int, PlacedPiece>>,
        remaining: MutableMap<String, Int>,
        currentPath: List<PlacedPiece>,
        grid: SpatialGrid
    ) {
        resolveUnusedExitsSerial(unusedExits, remaining, currentPath, grid)
    }

    private fun resolveUnusedExitsSerial(
        unusedExits: List<Pair<Int, PlacedPiece>>,
        remaining: MutableMap<String, Int>,
        currentPath: List<PlacedPiece>,
        grid: SpatialGrid
    ) {
        if (unusedExits.isEmpty()) {
            val sequence = canonicalizer.getPathSequence(currentPath)
            val hasDeadEnds = currentPath.any { it.isDeadEnd || it.deadEndExits.isNotEmpty() }
            val canonical = canonicalizer.getCanonicalSequence(sequence, isCycle = !hasDeadEnds)
            if (seenSolutionSequences.add(canonical)) {
                val features = scorer.extractFeatures(currentPath)
                val breakdown = scorer.getScoreBreakdown(features)
                solutions.add(ScoredSolution(currentPath.toList(), breakdown))
                updateGlobalUsage(currentPath)
            }
            return
        }

        val (idx1, s1) = unusedExits[0]
        val pose1 = s1.allConnectorPoses[idx1]

        for (i in 1 until unusedExits.size) {
            val (idx2, s2) = unusedExits[i]
            val pose2 = s2.allConnectorPoses[idx2]
            val targetPose = pose2.copy(rotation = (pose2.rotation + 180.0) % 360.0)

            val sidingPath = mutableListOf<PlacedPiece>()
            val tempPath = currentPath.toMutableList()

            val maxSiding = minOf(20, remaining.values.sum())
            if (findPathBetweenBacktrack(pose1, targetPose, remaining, tempPath, sidingPath, grid, maxSiding)) {
                val nextUnused = unusedExits.filterIndexed { index, _ -> index != 0 && index != i }
                resolveUnusedExitsSerial(nextUnused, remaining, tempPath, grid)

                for (p in sidingPath) {
                    val invId = getInventoryId(p.definition.id)
                    remaining[invId] = (remaining[invId] ?: 0) + 1
                    grid.remove(p)
                }
            }
        }

        val switchCount = currentPath.count { it.definition.type == TrackType.SWITCH }
        if (switchCount % 2 != 0 && deadEndSolutionCount < 1) {
            val (exitIdx, switch) = unusedExits[0]
            val startPose = switch.allConnectorPoses[exitIdx]
            val currentBranch = mutableListOf<PlacedPiece>()
            searchBranchRecursive(switch, exitIdx, startPose, remaining, currentPath, currentBranch, 0, unusedExits.drop(1), grid)
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
        otherUnused: List<Pair<Int, PlacedPiece>>,
        grid: SpatialGrid
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
                val sequence = canonicalizer.getPathSequence(pathWithDeadEnd)
                val canonical = canonicalizer.getCanonicalSequence(sequence, isCycle = false)
                if (seenSolutionSequences.add(canonical)) {
                    val features = scorer.extractFeatures(pathWithDeadEnd)
                    val breakdown = scorer.getScoreBreakdown(features)
                    solutions.add(ScoredSolution(pathWithDeadEnd, breakdown))
                    updateGlobalUsage(pathWithDeadEnd)
                    deadEndSolutionCount++
                }
            } else {
                resolveUnusedExitsSerial(otherUnused, remaining, pathWithDeadEnd, grid)
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
                        if (isCollision(nextPiece, mainPath + currentBranch, grid)) continue

                        remaining[id] = count - 1
                        currentBranch.add(nextPiece)
                        grid.add(nextPiece)
                        searchBranchRecursive(switch, exitIdx, nextPiece.exitPose, remaining, mainPath, currentBranch, depth + 1, otherUnused, grid)
                        grid.remove(nextPiece)
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
        grid: SpatialGrid,
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

            if (isCollision(nextPiece, path, grid)) continue

            remaining[inventoryId] = count - 1
            path.add(nextPiece)
            sidingPath.add(nextPiece)
            grid.add(nextPiece)

            if (findPathBetweenBacktrack(nextPiece.exitPose, targetPose, remaining, path, sidingPath, grid, maxDepth - 1)) {
                return true
            }

            grid.remove(nextPiece)
            sidingPath.removeAt(sidingPath.size - 1)
            path.removeAt(path.size - 1)
            remaining[inventoryId] = count
        }
        sidingCache[cacheKey] = null
        return false
    }

    private fun isCollision(newPiece: PlacedPiece, path: List<PlacedPiece>, grid: SpatialGrid): Boolean {
        val COLLISION_DIST_SQ = 7.0 * 7.0

        if (path.size < 25) {
            val newPoints = newPiece.checkpointPoses
            for (i in 1 until path.size) {
                val oldPoints = path[i].checkpointPoses
                for (np in newPoints) {
                    for (op in oldPoints) {
                        if (np.distanceSq(op) < COLLISION_DIST_SQ) return true
                    }
                }
            }
        } else {
            if (grid.checkCollision(newPiece, COLLISION_DIST_SQ)) return true
        }

        val firstPiece = path.firstOrNull()
        if (firstPiece != null) {
            val newPoints = newPiece.checkpointPoses
            val startPoints = firstPiece.checkpointPoses
            for (np in newPoints) {
                for (sp in startPoints) {
                    if (np.distanceSq(sp) < COLLISION_DIST_SQ) {
                        if (newPiece.exitPose.distanceSq(startPose) > (tolerancePos * 10).pow(2)) return true
                    }
                }
            }
        }
        return false
    }
}
