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
            sol.map { it.definition.id }
        }.toSet()

        assertEquals(solutions.size, uniqueSolutions.size, "Found duplicate solutions!")
    }
}
