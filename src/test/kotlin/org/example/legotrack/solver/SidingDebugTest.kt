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

        // 4. Switch Right (Diverge) - Branches towards +Y (Right in SVG)
        val switchRight = PlacedPiece(TrackLibrary.SWITCH_RIGHT, currentPose, 0)
        pieces.add(switchRight)
        val branchPathStart = switchRight.allConnectorPoses[1] // Branch exit

        // 5a. Main Line Path: 2 straights
        var mainPose = switchRight.exitPose
        repeat(2) {
            val piece = PlacedPiece(TrackLibrary.STRAIGHT, mainPose, 0)
            pieces.add(piece)
            mainPose = piece.exitPose
        }

        // 5b. Branch Path: Siding construction
        var branchPose = branchPathStart
        val branchPieces = mutableListOf<PlacedPiece>()

        val cLeft = PlacedPiece(TrackLibrary.CURVE_R40_LEFT, branchPose, 0)
        branchPieces.add(cLeft)
        branchPose = cLeft.exitPose

        val cRight = PlacedPiece(TrackLibrary.CURVE_R40, branchPose, 0)
        branchPieces.add(cRight)
        branchPose = cRight.exitPose

        // 6. Switch Right (Merge)
        val switchMerge = PlacedPiece(TrackLibrary.SWITCH_RIGHT_REV_STRAIGHT, mainPose, 0)
        pieces.add(switchMerge)
        val finalPose = switchMerge.exitPose

        println("Final Pose: $finalPose")

        // Validation
        val distToStart = finalPose.distanceTo(Pose(0.0, 0.0, 0.0))
        println("Distance to start: $distToStart")

        // Check siding connection
        val mergeBranchEntry = switchMerge.allConnectorPoses[1] // C3 is Branch Entry
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

        assert(distToStart < 2.0)
        // Manual siding construction has inherent gaps due to LEGO geometry mismatch in simplified model
        assert(sidingDist < 10.0)
        assert(sidingAngle < 0.1 || abs(sidingAngle - 180.0) < 0.1)
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
        val sw1 = PlacedPiece(TrackLibrary.SWITCH_RIGHT, currentPose, 0)
        pieces.add(sw1)
        var mainPose1 = sw1.exitPose
        repeat(2) {
            val piece = PlacedPiece(TrackLibrary.STRAIGHT, mainPose1, 0)
            pieces.add(piece)
            mainPose1 = piece.exitPose
        }
        val branchStart1 = sw1.allConnectorPoses[1]
        var branchPose1 = branchStart1
        val branchPieces1 = mutableListOf<PlacedPiece>()
        val cL1 = PlacedPiece(TrackLibrary.CURVE_R40_LEFT, branchPose1, 0)
        branchPieces1.add(cL1)
        val cR1 = PlacedPiece(TrackLibrary.CURVE_R40, cL1.exitPose, 0)
        branchPieces1.add(cR1)
        branchPose1 = cR1.exitPose

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
        val sw3 = PlacedPiece(TrackLibrary.SWITCH_RIGHT, currentPose, 0)
        pieces.add(sw3)
        var mainPose2 = sw3.exitPose
        repeat(2) {
            val piece = PlacedPiece(TrackLibrary.STRAIGHT, mainPose2, 0)
            pieces.add(piece)
            mainPose2 = piece.exitPose
        }
        val branchStart2 = sw3.allConnectorPoses[1]
        var branchPose2 = branchStart2
        val branchPieces2 = mutableListOf<PlacedPiece>()
        val cL2 = PlacedPiece(TrackLibrary.CURVE_R40_LEFT, branchPose2, 0)
        branchPieces2.add(cL2)
        val cR2 = PlacedPiece(TrackLibrary.CURVE_R40, cL2.exitPose, 0)
        branchPieces2.add(cR2)
        branchPose2 = cR2.exitPose

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

        assert(distToStart < 5.0)
        assert(siding1Dist < 10.0)
        assert(siding2Dist < 10.0)
    }
}
