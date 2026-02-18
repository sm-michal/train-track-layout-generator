package org.example.legotrack.solver

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DuplicateTest {
    @Test
    fun testNoDuplicates() {
        val inventory = mapOf(
            "straight" to 16,
            "curve_r40" to 16
        )
        val solver = Solver(inventory, maxSolutions = 10)
        val solutions = solver.solve()

        val uniqueSolutions = solutions.map { sol ->
            sol.map { piece ->
                val deadEnds = piece.deadEndExits.joinToString(",")
                "${piece.definition.id}:${piece.chosenExitIndex}:${piece.isDeadEnd}:${deadEnds}"
            }
        }.toSet()

        assertEquals(solutions.size, uniqueSolutions.size, "Found duplicate solutions!")
    }

    @Test
    fun testVisualDuplicates() {
        val inventory = mapOf(
            "curve_r40" to 16
        )
        // Without visual duplicate detection, it would find 2 (clockwise and counter-clockwise)
        // With it, there should only be 1 unique circle layout.
        val solver = Solver(inventory, maxSolutions = 10)
        val solutions = solver.solve()

        assertEquals(1, solutions.size, "Should only find one unique circle layout")
    }

    @Test
    fun testOvalDuplicates() {
        val inventory = mapOf(
            "straight" to 4,
            "curve_r40" to 16
        )
        val solver = Solver(inventory, maxSolutions = 100)
        val solutions = solver.solve()

        // Verify all returned solutions have unique canonical forms (redundant but good for testing)
        val mirrorMap = mapOf(
            "straight" to "straight",
            "curve_r40" to "curve_r40_right",
            "curve_r40_right" to "curve_r40",
            "switch_left" to "switch_right",
            "switch_right" to "switch_left",
            "switch_left:rev_s" to "switch_right:rev_s",
            "switch_right:rev_s" to "switch_left:rev_s",
            "switch_left:rev_b" to "switch_right:rev_b",
            "switch_right:rev_b" to "switch_left:rev_b"
        )

        val canonicals = solutions.map { sol ->
            val sequence = sol.map { piece ->
                val deadEnds = piece.deadEndExits.joinToString(",")
                "${piece.definition.id}:${piece.chosenExitIndex}:${piece.isDeadEnd}:${deadEnds}"
            }
            Solver.getCanonicalSequence(sequence, mirrorMap)
        }
        assertEquals(solutions.size, canonicals.toSet().size, "Found visually identical solutions!")
    }
}
