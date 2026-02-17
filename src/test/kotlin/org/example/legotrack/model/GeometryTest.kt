package org.example.legotrack.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class GeometryTest {

    @Test
    fun testStraightApply() {
        val start = Pose(0.0, 0.0, 0.0)
        val straight = Transform(16.0, 0.0, 0.0)
        val end = start.apply(straight)

        assertEquals(16.0, end.x, 0.0001)
        assertEquals(0.0, end.y, 0.0001)
        assertEquals(0.0, end.rotation, 0.0001)
    }

    @Test
    fun testCurveApply() {
        val start = Pose(0.0, 0.0, 0.0)
        val r = 40.0
        val angle = 22.5
        val rad = Math.toRadians(angle)
        val curve = Transform(
            dx = r * kotlin.math.sin(rad),
            dy = r * (1.0 - kotlin.math.cos(rad)),
            dRotation = angle
        )
        val end = start.apply(curve)

        assertEquals(r * kotlin.math.sin(rad), end.x, 0.0001)
        assertEquals(r * (1.0 - kotlin.math.cos(rad)), end.y, 0.0001)
        assertEquals(22.5, end.rotation, 0.0001)
    }

    @Test
    fun testCircle() {
        var current = Pose(0.0, 0.0, 0.0)
        val r = 40.0
        val angle = 22.5
        val rad = Math.toRadians(angle)
        val curve = Transform(
            dx = r * kotlin.math.sin(rad),
            dy = r * (1.0 - kotlin.math.cos(rad)),
            dRotation = angle
        )

        repeat(16) {
            current = current.apply(curve)
        }

        assertEquals(0.0, current.x, 0.0001)
        assertEquals(0.0, current.y, 0.0001)
        assertEquals(0.0, current.rotation, 0.0001)
    }
}
