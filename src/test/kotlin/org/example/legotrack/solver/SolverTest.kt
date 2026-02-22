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
        assertEquals(16, solutions[0].path.size)
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
    fun testSolveSiding() {
        val inventory = mapOf(
            "curve_r40" to 16,
            "straight" to 4,
            "switch_left" to 1,
            "switch_right" to 1
        )
        val solver = Solver(inventory, maxSolutions = 50)
        val solutions = solver.solve()

        assertTrue(solutions.isNotEmpty(), "Should find at least one solution")
        val sidingSolutions = solutions.filter { sol -> sol.path.count { it.definition.type == TrackType.SWITCH } == 2 }
        println("Found ${sidingSolutions.size} siding solutions")
    }
}
