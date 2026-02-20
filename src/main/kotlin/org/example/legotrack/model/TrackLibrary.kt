package org.example.legotrack.model

import kotlin.math.*

object TrackLibrary {
    private const val R40_RADIUS = 40.0
    private const val R40_ANGLE = 22.5
    private val R40_RAD = Math.toRadians(R40_ANGLE)

    val STRAIGHT = TrackPieceDefinition(
        id = "straight",
        type = TrackType.STRAIGHT,
        exits = listOf(Transform(16.0, 0.0, 0.0)),
        relativeCheckpoints = listOf(Transform(8.0, 0.0, 0.0))
    )

    val CURVE_R40_RIGHT = TrackPieceDefinition(
        id = "curve_r40_right",
        type = TrackType.CURVE,
        exits = listOf(
            Transform(
                dx = R40_RADIUS * sin(R40_RAD),
                dy = R40_RADIUS * (1.0 - cos(R40_RAD)),
                dRotation = R40_ANGLE
            )
        ),
        r = R40_RADIUS,
        arcAngle = R40_ANGLE,
        mirrorId = "curve_r40_left",
        relativeCheckpoints = listOf(
            Transform(
                dx = R40_RADIUS * sin(R40_RAD / 2.0),
                dy = R40_RADIUS * (1.0 - cos(R40_RAD / 2.0)),
                dRotation = 0.0
            )
        )
    )

    val CURVE_R40_LEFT = TrackPieceDefinition(
        id = "curve_r40_left",
        type = TrackType.CURVE,
        exits = listOf(
            Transform(
                dx = R40_RADIUS * sin(R40_RAD),
                dy = -R40_RADIUS * (1.0 - cos(R40_RAD)),
                dRotation = -R40_ANGLE
            )
        ),
        r = R40_RADIUS,
        arcAngle = -R40_ANGLE,
        mirrorId = "curve_r40_right",
        relativeCheckpoints = listOf(
            Transform(
                dx = R40_RADIUS * sin(R40_RAD / 2.0),
                dy = -R40_RADIUS * (1.0 - cos(R40_RAD / 2.0)),
                dRotation = 0.0
            )
        )
    )

    val SWITCH_LEFT = TrackPieceDefinition(
        id = "switch_left",
        type = TrackType.SWITCH,
        exits = listOf(
            Transform(32.0, 0.0, 0.0), // Straight
            Transform(32.693, -12.955, -22.5) // Branch
        ),
        mirrorId = "switch_right",
        relativeCheckpoints = listOf(
            Transform(8.0, 0.0, 0.0),
            Transform(16.0, 0.0, 0.0),
            Transform(24.0, 0.0, 0.0),
            Transform(16.346, -6.477, 0.0) // Branch mid
        )
    )

    val SWITCH_LEFT_REV_STRAIGHT = TrackPieceDefinition(
        id = "switch_left:rev_s",
        type = TrackType.SWITCH,
        exits = listOf(Transform(32.0, 0.0, 0.0)),
        allConnectors = listOf(
            Transform(32.0, 0.0, 0.0), // C1
            Transform(-0.693, 12.955, 157.5), // C3
            Transform(0.0, 0.0, 180.0) // C2 (entry)
        ),
        baseTransform = Transform(32.0, 0.0, 180.0),
        mirrorId = "switch_right:rev_s",
        relativeCheckpoints = listOf(
            Transform(8.0, 0.0, 0.0),
            Transform(16.0, 0.0, 0.0),
            Transform(24.0, 0.0, 0.0),
            Transform(16.346, -6.477, 0.0)
        )
    )

    val SWITCH_LEFT_REV_BRANCH = TrackPieceDefinition(
        id = "switch_left:rev_b",
        type = TrackType.SWITCH,
        exits = listOf(Transform(35.162, 0.542, 22.5)),
        allConnectors = listOf(
            Transform(35.162, 0.542, 22.5), // C1
            Transform(5.598, -11.703, 202.5), // C2
            Transform(0.0, 0.0, 180.0) // C3 (entry)
        ),
        baseTransform = Transform(35.162, 0.542, 202.5),
        mirrorId = "switch_right:rev_b",
        relativeCheckpoints = listOf(
            Transform(8.0, 0.0, 0.0),
            Transform(16.0, 0.0, 0.0),
            Transform(24.0, 0.0, 0.0),
            Transform(16.346, -6.477, 0.0)
        )
    )

    val SWITCH_RIGHT = TrackPieceDefinition(
        id = "switch_right",
        type = TrackType.SWITCH,
        exits = listOf(
            Transform(32.0, 0.0, 0.0), // Straight
            Transform(32.693, 12.955, 22.5) // Branch
        ),
        mirrorId = "switch_left",
        relativeCheckpoints = listOf(
            Transform(8.0, 0.0, 0.0),
            Transform(16.0, 0.0, 0.0),
            Transform(24.0, 0.0, 0.0),
            Transform(16.346, 6.477, 0.0) // Branch mid
        )
    )

    val SWITCH_RIGHT_REV_STRAIGHT = TrackPieceDefinition(
        id = "switch_right:rev_s",
        type = TrackType.SWITCH,
        exits = listOf(Transform(32.0, 0.0, 0.0)),
        allConnectors = listOf(
            Transform(32.0, 0.0, 0.0), // C1
            Transform(-0.693, -12.955, 202.5), // C3
            Transform(0.0, 0.0, 180.0) // C2 (entry)
        ),
        baseTransform = Transform(32.0, 0.0, 180.0),
        mirrorId = "switch_left:rev_s",
        relativeCheckpoints = listOf(
            Transform(8.0, 0.0, 0.0),
            Transform(16.0, 0.0, 0.0),
            Transform(24.0, 0.0, 0.0),
            Transform(16.346, 6.477, 0.0)
        )
    )

    val SWITCH_RIGHT_REV_BRANCH = TrackPieceDefinition(
        id = "switch_right:rev_b",
        type = TrackType.SWITCH,
        exits = listOf(Transform(35.162, -0.542, -22.5)),
        allConnectors = listOf(
            Transform(35.162, -0.542, -22.5), // C1
            Transform(5.598, 11.703, 157.5), // C2
            Transform(0.0, 0.0, 180.0) // C3 (entry)
        ),
        baseTransform = Transform(35.162, -0.542, 157.5),
        mirrorId = "switch_left:rev_b",
        relativeCheckpoints = listOf(
            Transform(8.0, 0.0, 0.0),
            Transform(16.0, 0.0, 0.0),
            Transform(24.0, 0.0, 0.0),
            Transform(16.346, 6.477, 0.0)
        )
    )
}
