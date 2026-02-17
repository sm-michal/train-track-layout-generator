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
    private val startPose = Pose(0.0, 0.0, 0.0)

    // Map of ID to Definition(s). For curves, one ID might map to both Left and Right definitions.
    private val pieceOptions = mutableMapOf<String, List<TrackPieceDefinition>>()

    init {
        // Initialize piece options based on known library pieces
        inventory.keys.forEach { id ->
            when (id) {
                "straight" -> pieceOptions[id] = listOf(TrackLibrary.STRAIGHT)
                "curve_r40" -> pieceOptions[id] = listOf(TrackLibrary.CURVE_R40, TrackLibrary.CURVE_R40_RIGHT)
                // Add more as needed
            }
        }
    }

    private val maxPieceLength: Double = 16.0 // Default

    fun solve(): List<List<PlacedPiece>> {
        solutions.clear()
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
                solutions.add(path.toList())
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
                    val nextPiece = PlacedPiece(pieceDef, currentPose)

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

    private fun isCollision(newPiece: PlacedPiece, path: List<PlacedPiece>): Boolean {
        val newMid = getMidpoint(newPiece)

        // Against path
        for (i in 0 until path.size - 1) {
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
        val exit = piece.exitPose
        return Pose((entry.x + exit.x) / 2.0, (entry.y + exit.y) / 2.0, 0.0)
    }
}
