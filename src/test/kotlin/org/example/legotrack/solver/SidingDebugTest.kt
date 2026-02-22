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

        // 1. Start at (0,0,0)
        // 2. Switch Right (Diverge)
        val sw1 = PlacedPiece(TrackLibrary.SWITCH_RIGHT, currentPose, 0)
        pieces.add(sw1)

        // Main line: 2 straights
        var mainPose = sw1.exitPose
        repeat(2) {
            val piece = PlacedPiece(TrackLibrary.STRAIGHT, mainPose, 0)
            pieces.add(piece)
            mainPose = piece.exitPose
        }

        // Branch line: 2 curves (left)
        val branchPieces = mutableListOf<PlacedPiece>()
        var branchPose = sw1.allConnectorPoses[1]
        repeat(2) {
            val piece = PlacedPiece(TrackLibrary.CURVE_R40_LEFT, branchPose, 0)
            branchPieces.add(piece)
            branchPose = piece.exitPose
        }

        // 3. Switch Left (Merge)
        val sw2 = PlacedPiece(TrackLibrary.SWITCH_LEFT_REV_STRAIGHT, mainPose, 0)
        pieces.add(sw2)

        // Validation
        val sidingDist = branchPose.distanceTo(sw2.allConnectorPoses[1])
        println("Siding gap distance: $sidingDist")

        // Visualize
        val renderer = SvgRenderer()
        val allPieces = pieces + branchPieces
        val svg = renderer.render(allPieces)
        File("target/siding_debug.svg").writeText(svg)

        assert(sidingDist < 0.1)
    }

    @Test
    fun testDoubleSidingConstruction() {
        val pieces = mutableListOf<PlacedPiece>()
        var currentPose = Pose(0.0, 0.0, 0.0)

        // Siding 1
        val sw1 = PlacedPiece(TrackLibrary.SWITCH_RIGHT, currentPose, 0)
        pieces.add(sw1)
        var mainPose = sw1.exitPose
        repeat(2) {
            val p = PlacedPiece(TrackLibrary.STRAIGHT, mainPose, 0)
            pieces.add(p)
            mainPose = p.exitPose
        }
        var branchPose = sw1.allConnectorPoses[1]
        val branchPieces = mutableListOf<PlacedPiece>()
        repeat(2) {
            val p = PlacedPiece(TrackLibrary.CURVE_R40_LEFT, branchPose, 0)
            branchPieces.add(p)
            branchPose = p.exitPose
        }
        val sw2 = PlacedPiece(TrackLibrary.SWITCH_LEFT_REV_STRAIGHT, mainPose, 0)
        pieces.add(sw2)

        // Gap check 1
        val siding1Dist = branchPose.distanceTo(sw2.allConnectorPoses[1])
        println("Siding 1 gap distance: $siding1Dist")

        // Siding 2 (continue from sw2 fork exit)
        currentPose = sw2.exitPose // This is Pose(64, 0, 180)

        // Turn around to continue forward
        val turnAround = mutableListOf<PlacedPiece>()
        repeat(8) {
            val p = PlacedPiece(TrackLibrary.CURVE_R40, currentPose, 0)
            turnAround.add(p)
            currentPose = p.exitPose
        }
        // Now facing 0 again

        val sw3 = PlacedPiece(TrackLibrary.SWITCH_RIGHT, currentPose, 0)
        pieces.addAll(turnAround)
        pieces.add(sw3)
        mainPose = sw3.exitPose
        repeat(2) {
            val p = PlacedPiece(TrackLibrary.STRAIGHT, mainPose, 0)
            pieces.add(p)
            mainPose = p.exitPose
        }
        branchPose = sw3.allConnectorPoses[1]
        repeat(2) {
            val p = PlacedPiece(TrackLibrary.CURVE_R40_LEFT, branchPose, 0)
            branchPieces.add(p)
            branchPose = p.exitPose
        }
        val sw4 = PlacedPiece(TrackLibrary.SWITCH_LEFT_REV_STRAIGHT, mainPose, 0)
        pieces.add(sw4)

        // Gap check 2
        val siding2Dist = branchPose.distanceTo(sw4.allConnectorPoses[1])
        println("Siding 2 gap distance: $siding2Dist")

        // Visualize
        val renderer = SvgRenderer()
        val allPieces = pieces + branchPieces
        val svg = renderer.render(allPieces)
        File("target/double_siding_debug.svg").writeText(svg)

        assert(siding1Dist < 0.1)
        assert(siding2Dist < 0.1)
    }
}
