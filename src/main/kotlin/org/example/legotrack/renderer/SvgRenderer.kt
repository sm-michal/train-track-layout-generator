package org.example.legotrack.renderer

import org.example.legotrack.model.PlacedPiece
import org.example.legotrack.model.Pose
import org.example.legotrack.model.TrackType
import kotlin.math.max
import kotlin.math.min

class SvgRenderer {
    fun render(pieces: List<PlacedPiece>): String {
        if (pieces.isEmpty()) return ""

        // Calculate bounding box
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        for (piece in pieces) {
            // Check entry and exit poses
            listOf(piece.pose, piece.exitPose).forEach { p ->
                minX = min(minX, p.x)
                minY = min(minY, p.y)
                maxX = max(maxX, p.x)
                maxY = max(maxY, p.y)
            }
        }

        // Add some margin (e.g., 10 units)
        val margin = 10.0
        minX -= margin
        minY -= margin
        maxX += margin
        maxY += margin

        val width = maxX - minX
        val height = maxY - minY

        val sb = StringBuilder()
        sb.append("""<svg viewBox="$minX $minY $width $height" xmlns="http://www.w3.org/2000/svg">""")
        sb.append("\n  <rect x=\"$minX\" y=\"$minY\" width=\"$width\" height=\"$height\" fill=\"#f0f0f0\" />\n")

        for (piece in pieces) {
            val pose = piece.pose
            val path = piece.definition.getSvgPath()
            sb.append("""  <g transform="translate(${pose.x}, ${pose.y}) rotate(${pose.rotation})">""")

            val strokeColor = when(piece.definition.type) {
                TrackType.STRAIGHT -> "blue"
                TrackType.CURVE -> "red"
            }

            sb.append("\n    <path d=\"$path\" fill=\"none\" stroke=\"$strokeColor\" stroke-width=\"0.5\" />\n")
            sb.append("  </g>\n")
        }

        sb.append("</svg>")
        return sb.toString()
    }
}
