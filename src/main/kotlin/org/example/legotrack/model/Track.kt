package org.example.legotrack.model

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
enum class TrackType {
    STRAIGHT,
    CURVE,
    SWITCH
}

data class TrackPieceDefinition(
    val id: String,
    val type: TrackType,
    val exits: List<Transform>,
    val r: Double = 0.0, // radius for curves
    val arcAngle: Double = 0.0, // angle for curves
    val mirrorId: String = id,
    val baseTransform: Transform = Transform(0.0, 0.0, 0.0), // Transform from entry to canonical origin
    val allConnectors: List<Transform> = exits + Transform(0.0, 0.0, 180.0), // All connector transforms relative to entry
    val relativeCheckpoints: List<Transform> = emptyList() // Custom checkpoints for collision detection
) {
    fun getSvgPaths(): List<String> {
        return when (type) {
            TrackType.STRAIGHT -> {
                val length = 16.0 // Standard straight length
                listOf("M 0 0 L $length 0")
            }
            TrackType.CURVE -> {
                val theta = Math.toRadians(abs(arcAngle))
                val sweep = if (arcAngle > 0) 1 else 0
                val r = abs(r)
                val x = r * sin(theta)
                val y = if (arcAngle > 0) r * (1 - cos(theta)) else -r * (1 - cos(theta))
                listOf("M 0 0 A $r $r 0 0 $sweep $x $y")
            }
            TrackType.SWITCH -> {
                // Return paths for standard switch geometry (32 length, 13 offset branch)
                // SVG Y+ is DOWN.
                // switch_right branches RIGHT (DOWN/+Y).
                // switch_left branches LEFT (UP/-Y).
                if (id.contains("right")) {
                    listOf(
                        "M 0 0 L 32 0",
                        "M 0 0 Q 16 0, 32.693 12.955"
                    )
                } else {
                    listOf(
                        "M 0 0 L 32 0",
                        "M 0 0 Q 16 0, 32.693 -12.955"
                    )
                }
            }
        }
    }
}

data class PlacedPiece(
    val definition: TrackPieceDefinition,
    val pose: Pose, // The entry pose
    val chosenExitIndex: Int = 0,
    val isDeadEnd: Boolean = false,
    val deadEndExits: List<Int> = emptyList()
) {
    val exitPose: Pose by lazy {
        pose.apply(definition.exits[chosenExitIndex])
    }

    val allConnectorPoses: List<Pose> by lazy {
        definition.allConnectors.map { pose.apply(it) }
    }

    val checkpointPoses: List<Pose> by lazy {
        definition.relativeCheckpoints.map { pose.apply(it) }
    }
}
