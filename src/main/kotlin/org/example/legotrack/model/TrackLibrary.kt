package org.example.legotrack.model

import kotlin.math.*

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
        arcAngle = R40_ANGLE,
        mirrorId = "curve_r40_right"
    )

    val CURVE_R40_RIGHT = TrackPieceDefinition(
        id = "curve_r40_right",
        type = TrackType.CURVE,
        transform = Transform(
            dx = R40_RADIUS * sin(R40_RAD),
            dy = -R40_RADIUS * (1.0 - cos(R40_RAD)),
            dRotation = -R40_ANGLE
        ),
        r = R40_RADIUS,
        arcAngle = -R40_ANGLE,
        mirrorId = "curve_r40"
    )
}
