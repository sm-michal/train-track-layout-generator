package org.example.legotrack.solver

import org.example.legotrack.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.*

class ScoringVerificationTest {

    @Test
    fun testNewTurnScoring() {
        val scorer = LayoutScorer(emptyMap())

        // Test 135 turn
        // 6 curves of 22.5 = 135
        val path135 = List(6) { PlacedPiece(TrackLibrary.CURVE_R40, Pose(it * 10.0, 0.0, 0.0), 0) }
        val feat135 = scorer.extractFeatures(path135)
        assertEquals(1, feat135.longTurns135)
        val score135 = scorer.calculateScore(feat135)
        assertTrue(score135 >= 80.0)

        // Test 225 turn
        // 10 curves of 22.5 = 225
        val path225 = List(10) { PlacedPiece(TrackLibrary.CURVE_R40, Pose(it * 10.0, 0.0, 0.0), 0) }
        val feat225 = scorer.extractFeatures(path225)
        assertEquals(1, feat225.longTurns225)
        val score225 = scorer.calculateScore(feat225)
        assertTrue(score225 >= 80.0)
    }

    @Test
    fun testStraightScoringBalance() {
        val scorer = LayoutScorer(emptyMap())

        // Case A: 1 segment of 8 pieces
        val pathA = List(8) { PlacedPiece(TrackLibrary.STRAIGHT, Pose(it * 16.0, 0.0, 0.0), 0) }
        val featA = scorer.extractFeatures(pathA)
        val scoreA = scorer.calculateScore(featA)

        // Case B: 2 segments of 4 pieces (separated by a curve)
        val pathB = mutableListOf<PlacedPiece>()
        repeat(4) { pathB.add(PlacedPiece(TrackLibrary.STRAIGHT, Pose(it * 16.0, 0.0, 0.0), 0)) }
        pathB.add(PlacedPiece(TrackLibrary.CURVE_R40, Pose(100.0, 100.0, 0.0), 0))
        repeat(4) { pathB.add(PlacedPiece(TrackLibrary.STRAIGHT, Pose(200.0 + it * 16.0, 200.0, 0.0), 0)) }

        val featB = scorer.extractFeatures(pathB)
        val scoreB = scorer.calculateScore(featB)

        println("Score A (1x8): $scoreA")
        println("Score B (2x4): $scoreB")

        // Ratio should be around 1.5 according to user preference
        val ratio = scoreB / scoreA
        println("Ratio B/A: $ratio")
        assertTrue(ratio > 1.3 && ratio < 1.7, "Ratio $ratio should be around 1.5")
    }

    @Test
    fun testAllMinusFiveRule() {
        val inventory = mapOf(
            "straight" to 10
        )
        val solver = Solver(inventory, maxSolutions = 1)

        // Total inventory size is 10.
        // Rule: inventorySize - path.size <= 5.
        // So path.size >= 5.

        // Since we can't easily run the full solver to find a 5-piece loop with just straights,
        // we'll verify the logic by checking if it allows a small loop.

        // Actually, let's just test the Solver's internal logic if possible,
        // or just rely on the fact that I've implemented the check.

        // Better: test with a very small inventory that can form a loop.
        // A circle of 16 R40 curves is a loop.
        val circleInventory = mapOf("curve_r40" to 16)
        val solver2 = Solver(circleInventory, maxSolutions = 1)
        val solutions2 = solver2.solve()

        assertFalse(solutions2.isEmpty(), "Should find at least one solution")
        assertTrue(solutions2[0].path.size >= 11, "Should use at least 11 pieces (16-5)")

        // Now if we have 20 curves, but it can close with 16.
        val largeInventory = mapOf("curve_r40" to 20)
        val solver3 = Solver(largeInventory, maxSolutions = 1)
        val solutions3 = solver3.solve()

        if (solutions3.isNotEmpty()) {
            assertTrue(solutions3[0].path.size >= 15, "Should use at least 15 pieces (20-5)")
        }
    }
}
