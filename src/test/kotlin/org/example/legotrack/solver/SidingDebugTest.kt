package org.example.legotrack.solver

import org.example.legotrack.model.*
import org.example.legotrack.renderer.SvgRenderer
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs

class SidingDebugTest {
    @Test
    fun testManualSidingConstruction() {
        val pieces = mutableListOf<PlacedPiece>()
        var currentPose = Pose(0.0, 0.0, 0.0)

        // 1. 8 curves (180 deg turn)
        repeat(8) {
            val piece = PlacedPiece(TrackLibrary.CURVE_R40, currentPose, 0)
            pieces.add(piece)
            currentPose = piece.exitPose
        }

        // 2. 6 straights
        repeat(6) {
            val piece = PlacedPiece(TrackLibrary.STRAIGHT, currentPose, 0)
            pieces.add(piece)
            currentPose = piece.exitPose
        }

        // 3. 8 curves (180 deg turn)
        repeat(8) {
            val piece = PlacedPiece(TrackLibrary.CURVE_R40, currentPose, 0)
            pieces.add(piece)
            currentPose = piece.exitPose
        }

        // Now we should be at roughly (-96, 0, 0)
        println("Pose before siding: $currentPose")

        // 4. Switch Left (Diverge)
        val switchLeft = PlacedPiece(TrackLibrary.SWITCH_LEFT, currentPose, 0)
        pieces.add(switchLeft)
        val mainPathStart = switchLeft.exitPose
        val branchPathStart = switchLeft.allConnectorPoses[1] // Branch exit

        // 5a. Main Line Path: 2 straights
        var mainPose = mainPathStart
        repeat(2) {
            val piece = PlacedPiece(TrackLibrary.STRAIGHT, mainPose, 0)
            pieces.add(piece)
            mainPose = piece.exitPose
        }

        // 5b. Branch Path: 2 curves (Right)
        var branchPose = branchPathStart
        val branchPieces = mutableListOf<PlacedPiece>()
        repeat(2) {
            val piece = PlacedPiece(TrackLibrary.CURVE_R40_RIGHT, branchPose, 0)
            branchPieces.add(piece)
            branchPose = piece.exitPose
        }
        // We don't add branchPieces to the 'pieces' list yet, as they are part of the siding connection

        // 6. Switch Right (Merge)
        // We use SWITCH_RIGHT_REV_STRAIGHT as the piece in the main loop
        val switchRight = PlacedPiece(TrackLibrary.SWITCH_RIGHT_REV_STRAIGHT, mainPose, 0)
        pieces.add(switchRight)
        val finalPose = switchRight.exitPose

        println("Final Pose: $finalPose")

        // Validation
        val distToStart = finalPose.distanceTo(Pose(0.0, 0.0, 0.0))
        val angleToStart = finalPose.angleDistanceTo(Pose(0.0, 0.0, 0.0))

        println("Distance to start: $distToStart")
        println("Angle to start: $angleToStart")

        // Check siding connection
        val mergeBranchEntry = switchRight.allConnectorPoses[1] // C3 is Branch Entry
        println("Branch path end: $branchPose")
        println("Merge branch entry: $mergeBranchEntry")

        val sidingDist = branchPose.distanceTo(mergeBranchEntry)
        val sidingAngle = branchPose.angleDistanceTo(mergeBranchEntry)
        println("Siding gap distance: $sidingDist")
        println("Siding gap angle: $sidingAngle")

        // Visualize
        val renderer = SvgRenderer()
        val allPieces = pieces + branchPieces
        val svg = renderer.render(allPieces)
        File("target/siding_debug.svg").writeText(svg)

        assert(distToStart < 0.1) { "Main loop not closed: $distToStart" }
        assert(angleToStart < 0.1) { "Main loop angle not closed: $angleToStart" }
        assert(sidingDist < 0.1) { "Siding not closed: $sidingDist" }
        // For the siding, they should be 180 degrees apart if one is OUT and one is IN?
        // Let's see what the current code thinks.
        // If they are 180 deg apart, angleDistanceTo will be 180.
        assert(sidingAngle < 0.1 || abs(sidingAngle - 180.0) < 0.1) { "Siding angle not aligned: $sidingAngle" }
    }

    @Test
    fun testSolverFindsSiding() {
        val inventory = mapOf(
            "curve_r40" to 18,
            "straight" to 8,
            "switch_left" to 1,
            "switch_right" to 1
        )
        // We want to find the siding. We might need a higher maxSolutions or wait.
        val solver = Solver(inventory, maxSolutions = 100)
        val solutions = solver.solve()

        println("Found ${solutions.size} solutions")
        val sidingSolutions = solutions.filter { sol ->
            sol.any { it.definition.id.contains("switch_left") } &&
            sol.any { it.definition.id.contains("switch_right") } &&
            sol.size > 20 // The oval with siding is 26 pieces
        }
        println("Found ${sidingSolutions.size} potential siding solutions")

        assert(sidingSolutions.isNotEmpty()) { "Solver could not find any siding solutions" }

        // Save one siding solution
        val renderer = SvgRenderer()
        val svg = renderer.render(sidingSolutions[0])
        File("target/solver_siding.svg").writeText(svg)
    }
}
