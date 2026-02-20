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
    fun testDoubleSidingConstruction() {
        val pieces = mutableListOf<PlacedPiece>()
        var currentPose = Pose(0.0, 0.0, 0.0)

        // 1. 8 curves (180 deg turn)
        repeat(8) {
            val piece = PlacedPiece(TrackLibrary.CURVE_R40, currentPose, 0)
            pieces.add(piece)
            currentPose = piece.exitPose
        }

        // 2. First Siding
        val sw1 = PlacedPiece(TrackLibrary.SWITCH_LEFT, currentPose, 0)
        pieces.add(sw1)
        val branchStart1 = sw1.allConnectorPoses[1]

        // Main line: 2 straights
        var mainPose1 = sw1.exitPose
        repeat(2) {
            val piece = PlacedPiece(TrackLibrary.STRAIGHT, mainPose1, 0)
            pieces.add(piece)
            mainPose1 = piece.exitPose
        }

        // Branch line: 2 curves (right)
        var branchPose1 = branchStart1
        val branchPieces1 = mutableListOf<PlacedPiece>()
        repeat(2) {
            val piece = PlacedPiece(TrackLibrary.CURVE_R40_RIGHT, branchPose1, 0)
            branchPieces1.add(piece)
            branchPose1 = piece.exitPose
        }

        val sw2 = PlacedPiece(TrackLibrary.SWITCH_RIGHT_REV_STRAIGHT, mainPose1, 0)
        pieces.add(sw2)
        currentPose = sw2.exitPose

        // 3. 8 curves (180 deg turn)
        repeat(8) {
            val piece = PlacedPiece(TrackLibrary.CURVE_R40, currentPose, 0)
            pieces.add(piece)
            currentPose = piece.exitPose
        }

        // 4. Second Siding
        val sw3 = PlacedPiece(TrackLibrary.SWITCH_LEFT, currentPose, 0)
        pieces.add(sw3)
        val branchStart2 = sw3.allConnectorPoses[1]

        // Main line: 2 straights
        var mainPose2 = sw3.exitPose
        repeat(2) {
            val piece = PlacedPiece(TrackLibrary.STRAIGHT, mainPose2, 0)
            pieces.add(piece)
            mainPose2 = piece.exitPose
        }

        // Branch line: 2 curves (right)
        var branchPose2 = branchStart2
        val branchPieces2 = mutableListOf<PlacedPiece>()
        repeat(2) {
            val piece = PlacedPiece(TrackLibrary.CURVE_R40_RIGHT, branchPose2, 0)
            branchPieces2.add(piece)
            branchPose2 = piece.exitPose
        }

        val sw4 = PlacedPiece(TrackLibrary.SWITCH_RIGHT_REV_STRAIGHT, mainPose2, 0)
        pieces.add(sw4)
        currentPose = sw4.exitPose

        // Validation
        val distToStart = currentPose.distanceTo(Pose(0.0, 0.0, 0.0))
        println("Double siding distance to start: $distToStart")

        val siding1Dist = branchPose1.distanceTo(sw2.allConnectorPoses[1])
        println("Siding 1 gap distance: $siding1Dist")

        val siding2Dist = branchPose2.distanceTo(sw4.allConnectorPoses[1])
        println("Siding 2 gap distance: $siding2Dist")

        // Visualize
        val renderer = SvgRenderer()
        val allPieces = pieces + branchPieces1 + branchPieces2
        val svg = renderer.render(allPieces)
        File("target/double_siding_debug.svg").writeText(svg)

        assert(distToStart < 0.1)
        assert(siding1Dist < 0.1)
        assert(siding2Dist < 0.1)
    }

}
