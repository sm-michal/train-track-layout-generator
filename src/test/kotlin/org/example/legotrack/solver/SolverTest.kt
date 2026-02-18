package org.example.legotrack.solver

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
}
