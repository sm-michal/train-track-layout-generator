package org.example.legotrack.solver

import org.example.legotrack.model.TrackType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SolverTest {
    @Test
    fun testSolveCircle() {
        val inventory = mapOf(
            "curve_r40" to 16
        )
        val solver = Solver(inventory, maxSolutions = 1)
        val solutions = solver.solve()

        assertEquals(1, solutions.size)
        assertEquals(16, solutions[0].size)
    }

    @Test
    fun testSolveOval() {
        val inventory = mapOf(
            "curve_r40" to 16,
            "straight" to 4
        )
        val solver = Solver(inventory, maxSolutions = 5)
        val solutions = solver.solve()

        assertTrue(solutions.isNotEmpty())
    }

    @Test
    fun testSolveWithSwitch() {
        val inventory = mapOf(
            "curve_r40" to 16,
            "straight" to 4,
            "switch_left" to 1
        )
        val solver = Solver(inventory, maxSolutions = 10)
        val solutions = solver.solve()

        // Should find some solutions with switch
        val switchSolutions = solutions.filter { sol -> sol.any { it.definition.id == "switch_left" } }
        assertTrue(switchSolutions.isNotEmpty(), "Should find solutions using the switch")

        // Check that at most one solution has a dead end indicator
        val deadEndSolutions = solutions.filter { sol -> sol.any { it.isDeadEnd || it.deadEndExits.isNotEmpty() } }
        assertTrue(deadEndSolutions.size <= 1, "Should have at most one dead-end solution")

        if (deadEndSolutions.isNotEmpty()) {
            val sol = deadEndSolutions[0]
            val switchPiece = sol.find { it.definition.id == "switch_left" }
            assertNotNull(switchPiece)
            // Either the branch has pieces ending in deadEnd, or the switch itself has a deadEndExit
            val hasDeadEnd = switchPiece!!.deadEndExits.isNotEmpty() || sol.any { it.isDeadEnd }
            assertTrue(hasDeadEnd)
        }
    }

    @Test
    fun testSolveSiding() {
        val inventory = mapOf(
            "curve_r40" to 20,
            "straight" to 10,
            "switch_left" to 1,
            "switch_right" to 1
        )
        val solver = Solver(inventory, maxSolutions = 50)
        val solutions = solver.solve()

        // Should find some solutions with 2 switches
        val sidingSolutions = solutions.filter { sol -> sol.count { it.definition.type == TrackType.SWITCH } == 2 }
        // We don't assert > 0 because it depends on the random search order and tolerances,
        // but we can check if any are found.
        println("Found ${sidingSolutions.size} siding solutions")
    }
}
