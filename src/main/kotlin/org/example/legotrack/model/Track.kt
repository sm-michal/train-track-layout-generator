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
    val mirrorId: String = id
) {
    fun getSvgPaths(): List<String> {
        return when (type) {
            TrackType.STRAIGHT -> {
                val length = exits[0].dx
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
                // Return paths for all exits
                exits.mapIndexed { index, transform ->
                    if (index == 0) { // Straight path
                        "M 0 0 L ${transform.dx} 0"
                    } else { // Branch path
                        // Using a quadratic Bezier to approximate the curve
                        "M 0 0 Q ${transform.dx * 0.5} 0, ${transform.dx} ${transform.dy}"
                    }
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

    val allExitPoses: List<Pose> by lazy {
        definition.exits.map { pose.apply(it) }
    }
}
