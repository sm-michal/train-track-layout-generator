package org.example.legotrack.model

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
enum class TrackType {
    STRAIGHT,
    CURVE
}

data class TrackPieceDefinition(
    val id: String,
    val type: TrackType,
    val transform: Transform,
    val r: Double = 0.0, // radius for curves
    val arcAngle: Double = 0.0 // angle for curves
) {
    fun getSvgPath(): String {
        return when (type) {
            TrackType.STRAIGHT -> {
                val length = transform.dx
                "M 0 -4 L $length -4 L $length 4 L 0 4 Z " + // Sleeper area
                "M 0 -2.5 L $length -2.5 " + // Rail 1
                "M 0 2.5 L $length 2.5"      // Rail 2
            }
            TrackType.CURVE -> {
                val theta = Math.toRadians(abs(arcAngle))
                val sweep = if (arcAngle > 0) 1 else 0 // SVG sweep flag
                // This is a bit complex for a single path string if we want multiple lines.
                // Let's just return a simple arc for now.
                val r = abs(r)
                val x = r * sin(theta)
                val y = if (arcAngle > 0) r * (1 - cos(theta)) else -r * (1 - cos(theta))

                // For SVG 'a' command: rx ry x-axis-rotation large-arc-flag sweep-flag x y
                "M 0 0 A $r $r 0 0 $sweep $x $y"
            }
        }
    }
}

data class PlacedPiece(
    val definition: TrackPieceDefinition,
    val pose: Pose // The entry pose
) {
    val exitPose: Pose by lazy {
        pose.apply(definition.transform)
    }
}

object TrackLibrary {
    private const val R40_RADIUS = 40.0
    private const val R40_ANGLE = 22.5
    private val R40_RAD = Math.toRadians(R40_ANGLE)

    val STRAIGHT = TrackPieceDefinition(
        id = "straight",
        type = TrackType.STRAIGHT,
        transform = Transform(16.0, 0.0, 0.0)
    )

    val CURVE_R40 = TrackPieceDefinition(
        id = "curve_r40",
        type = TrackType.CURVE,
        transform = Transform(
            dx = R40_RADIUS * sin(R40_RAD),
            dy = R40_RADIUS * (1.0 - cos(R40_RAD)),
            dRotation = R40_ANGLE
        ),
        r = R40_RADIUS,
        arcAngle = R40_ANGLE
    )

    // Note: To support Right turn, we can either have a separate definition
    // or allow the solver to "flip" the piece.
    // LEGO curves are reversible.
    val CURVE_R40_RIGHT = TrackPieceDefinition(
        id = "curve_r40_right",
        type = TrackType.CURVE,
        transform = Transform(
            dx = R40_RADIUS * sin(R40_RAD),
            dy = -R40_RADIUS * (1.0 - cos(R40_RAD)),
            dRotation = -R40_ANGLE
        ),
        r = R40_RADIUS,
        arcAngle = -R40_ANGLE
    )
}
