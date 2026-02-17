package org.example.legotrack.model

import kotlin.math.*

data class Pose(val x: Double, val y: Double, val rotation: Double) {
    fun apply(transform: Transform): Pose {
        val rad = Math.toRadians(rotation)
        val cos = cos(rad)
        val sin = sin(rad)

        val newX = x + transform.dx * cos - transform.dy * sin
        val newY = y + transform.dx * sin + transform.dy * cos
        val newRotation = (rotation + transform.dRotation) % 360.0

        return Pose(newX, newY, if (newRotation < 0) newRotation + 360.0 else newRotation)
    }

    fun distanceTo(other: Pose): Double {
        return sqrt((x - other.x).pow(2) + (y - other.y).pow(2))
    }

    fun angleDistanceTo(other: Pose): Double {
        val diff = abs(rotation - other.rotation) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }
}

data class Transform(val dx: Double, val dy: Double, val dRotation: Double)
