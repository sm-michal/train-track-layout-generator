package org.example.legotrack.renderer

import org.example.legotrack.model.PlacedPiece
import org.example.legotrack.model.Pose
import org.example.legotrack.model.TrackLibrary
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class SvgRendererTest {
    @Test
    fun testRenderCircle() {
        val pieces = mutableListOf<PlacedPiece>()
        var currentPose = Pose(0.0, 0.0, 0.0)

        repeat(16) {
            val piece = PlacedPiece(TrackLibrary.CURVE_R40_RIGHT, currentPose)
            pieces.add(piece)
            currentPose = piece.exitPose
        }

        val renderer = SvgRenderer()
        val svg = renderer.render(pieces)

        assertNotNull(svg)
        assertTrue(svg.contains("<svg"))
        assertTrue(svg.contains("</svg>"))

        // Save to a file for manual inspection if needed
        File("output/test_circle.svg").parentFile.mkdirs()
        File("output/test_circle.svg").writeText(svg)
    }
}
