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
            // Check entry and all connector poses
            (listOf(piece.pose) + piece.allConnectorPoses).forEach { p ->
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
            val basePose = piece.pose.apply(piece.definition.baseTransform)
            val paths = piece.definition.getSvgPaths()
            sb.append("""  <g transform="translate(${basePose.x}, ${basePose.y}) rotate(${basePose.rotation})">""")

            val strokeColor = when(piece.definition.type) {
                TrackType.STRAIGHT -> "blue"
                TrackType.CURVE -> "red"
                TrackType.SWITCH -> "green"
            }

            for (path in paths) {
                sb.append("\n    <path d=\"$path\" fill=\"none\" stroke=\"$strokeColor\" stroke-width=\"0.5\" />\n")
            }
            sb.append("  </g>\n")

            // Connection point indicator at the start of the piece
            sb.append("  <circle cx=\"${piece.pose.x}\" cy=\"${piece.pose.y}\" r=\"1\" fill=\"black\" />\n")

            // Dead end indicators (stop signs)
            if (piece.isDeadEnd) {
                val exit = piece.exitPose
                sb.append("  <circle cx=\"${exit.x}\" cy=\"${exit.y}\" r=\"2\" fill=\"red\" stroke=\"white\" stroke-width=\"0.5\" />\n")
                sb.append("  <line x1=\"${exit.x-1.2}\" y1=\"${exit.y}\" x2=\"${exit.x+1.2}\" y2=\"${exit.y}\" stroke=\"white\" stroke-width=\"0.5\" />\n")
            }
            for (exitIdx in piece.deadEndExits) {
                val exit = piece.allConnectorPoses[exitIdx]
                sb.append("  <circle cx=\"${exit.x}\" cy=\"${exit.y}\" r=\"2\" fill=\"red\" stroke=\"white\" stroke-width=\"0.5\" />\n")
                sb.append("  <line x1=\"${exit.x-1.2}\" y1=\"${exit.y}\" x2=\"${exit.x+1.2}\" y2=\"${exit.y}\" stroke=\"white\" stroke-width=\"0.5\" />\n")
            }
        }

        sb.append("</svg>")
        return sb.toString()
    }
}
