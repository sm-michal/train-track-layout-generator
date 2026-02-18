package org.example.legotrack.solver

import org.example.legotrack.model.*
import kotlin.math.abs

class Solver(
    val inventory: Map<String, Int>, // ID to count
    val maxSolutions: Int = 1,
    val tolerancePos: Double = 0.5,
    val toleranceAngle: Double = 1.0
) {
    private val solutions = mutableListOf<List<PlacedPiece>>()
    private var deadEndSolutionCount = 0
    private val seenSolutionSequences = mutableSetOf<List<String>>()
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
                "switch_left" -> pieceOptions[id] = listOf(TrackLibrary.SWITCH_LEFT)
                "switch_right" -> pieceOptions[id] = listOf(TrackLibrary.SWITCH_RIGHT)
                // Add more as needed
            }
        }
    }

    private val maxPieceLength: Double = 16.0 // Default

    fun solve(): List<List<PlacedPiece>> {
        solutions.clear()
        deadEndSolutionCount = 0
        seenSolutionSequences.clear()
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
                val usedSwitches = path.filter { it.definition.type == TrackType.SWITCH }
                val nSwitches = usedSwitches.size

                val unusedExits = mutableListOf<Pair<Int, PlacedPiece>>()
                for (piece in path) {
                    if (piece.definition.type == TrackType.SWITCH) {
                        unusedExits.add((1 - piece.chosenExitIndex) to piece)
                    }
                }

                if (unusedExits.isEmpty()) {
                    val sequence = getPathSequence(path)
                    val canonical = getCanonicalSequence(sequence, isCycle = true)
                    if (seenSolutionSequences.add(canonical)) {
                        solutions.add(path.toList())
                    }
                } else if (unusedExits.size == 1) {
                    if (deadEndSolutionCount < 1) {
                        val (exitIdx, switch) = unusedExits[0]
                        tryAddDeadEnd(switch, exitIdx, remaining, path)
                    }
                } else {
                    // More than one unused exit.
                    // To be valid, we must either connect them all (even) or connect all but one (odd).
                    // For now, let's try to connect pairs of exits to form sidings.
                    tryConnectAll(unusedExits, remaining, path)
                }
                return
            }
        }

        // Pruning
        val distToStart = currentPose.distanceTo(startPose)
        val remainingCount = remaining.values.sum()
        if (distToStart > remainingCount * maxPieceLength + tolerancePos) {
            return
        }

        // Try each piece type in inventory
        for (id in remaining.keys) {
            val count = remaining[id]!!
            if (count > 0) {
                // Try each option for this piece type (e.g. Left/Right for curves)
                val options = pieceOptions[id] ?: continue
                for (pieceDef in options) {
                    // Try each exit for this piece
                    for (exitIndex in pieceDef.exits.indices) {
                        val nextPiece = PlacedPiece(pieceDef, currentPose, exitIndex)

                        if (isCollision(nextPiece, path)) continue

                        remaining[id] = count - 1
                        path.add(nextPiece)

                        backtrack(nextPiece.exitPose, remaining, path)

                        path.removeAt(path.size - 1)
                        remaining[id] = count

                        if (solutions.size >= maxSolutions) return
                    }
                }
            }
        }
    }

    private fun getPathSequence(path: List<PlacedPiece>): List<String> {
        return path.map { piece ->
            val deadEnds = piece.deadEndExits.joinToString(",")
            "${piece.definition.id}:${piece.chosenExitIndex}:${piece.isDeadEnd}:${deadEnds}"
        }
    }

    private fun tryConnectAll(
        unusedExits: List<Pair<Int, PlacedPiece>>,
        remaining: MutableMap<String, Int>,
        mainPath: List<PlacedPiece>
    ) {
        if (unusedExits.size == 2) {
            val (idx1, s1) = unusedExits[0]
            val (idx2, s2) = unusedExits[1]
            val pose1 = s1.allExitPoses[idx1]
            val pose2 = s2.allExitPoses[idx2]

            val path = findPathBetween(pose1, pose2, remaining, mainPath, 4)
            if (path != null) {
                val fullPath = mainPath + path
                val sequence = getPathSequence(fullPath)
                val canonical = getCanonicalSequence(sequence, isCycle = false)
                if (seenSolutionSequences.add(canonical)) {
                    solutions.add(fullPath)
                }
            }
        }
    }

    private fun findPathBetween(
        startPose: Pose,
        targetPose: Pose,
        remaining: MutableMap<String, Int>,
        forbiddenPieces: List<PlacedPiece>,
        maxDepth: Int
    ): List<PlacedPiece>? {
        if (startPose.distanceTo(targetPose) < tolerancePos &&
            startPose.angleDistanceTo(targetPose) < toleranceAngle) {
            return emptyList()
        }
        if (maxDepth <= 0) return null

        for (id in remaining.keys) {
            val count = remaining[id]!!
            if (count > 0) {
                val options = pieceOptions[id] ?: continue
                for (pieceDef in options) {
                    for (exitIndex in pieceDef.exits.indices) {
                        val nextPiece = PlacedPiece(pieceDef, startPose, exitIndex)
                        if (isCollision(nextPiece, forbiddenPieces)) continue

                        remaining[id] = count - 1
                        val rest = findPathBetween(nextPiece.exitPose, targetPose, remaining, forbiddenPieces + nextPiece, maxDepth - 1)
                        if (rest != null) {
                            remaining[id] = count
                            return listOf(nextPiece) + rest
                        }
                        remaining[id] = count
                    }
                }
            }
        }
        return null
    }

    private fun tryAddDeadEnd(
        switch: PlacedPiece,
        exitIdx: Int,
        remaining: MutableMap<String, Int>,
        mainPath: List<PlacedPiece>
    ) {
        val startPose = switch.allExitPoses[exitIdx]
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
        val newMid = getMidpoint(newPiece)

        // Against path
        for (i in 0 until path.size) {
            val oldMid = getMidpoint(path[i])
            if (newMid.distanceTo(oldMid) < 7.5) return true
        }

        // Against start
        if (path.size > 2) {
            // Roughly estimate the midpoint of the first piece (which starts at startPose)
            val firstPiece = path.firstOrNull()
            val startPieceMid = firstPiece?.let { getMidpoint(it) } ?: startPose

            if (newMid.distanceTo(startPieceMid) < 7.5) {
                // If we are close to the start but NOT about to close the loop, it's a collision
                if (newPiece.exitPose.distanceTo(startPose) > tolerancePos * 5) return true
            }
        }

        return false
    }

    private fun getMidpoint(piece: PlacedPiece): Pose {
        val entry = piece.pose
        val exit = piece.exitPose // This is the chosen exit
        return Pose((entry.x + exit.x) / 2.0, (entry.y + exit.y) / 2.0, 0.0)
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
