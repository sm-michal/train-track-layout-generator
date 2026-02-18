package org.example.legotrack

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.legotrack.renderer.SvgRenderer
import org.example.legotrack.solver.Solver
import java.io.File

@Serializable
data class GenerationRequest(
    val inventory: Map<String, Int>,
    val maxSolutions: Int = 1,
    val outputDir: String = "output"
)

fun main(args: Array<String>) {
    val inputPath = args.getOrNull(0) ?: "input.json"
    val inputFile = File(inputPath)

    if (!inputFile.exists()) {
        println("Input file $inputPath not found.")
        println("Creating a sample input.json...")
        val sample = GenerationRequest(
            inventory = mapOf("straight" to 12, "curve_r40" to 16),
            maxSolutions = 3
        )
        inputFile.writeText(Json { prettyPrint = true }.encodeToString(GenerationRequest.serializer(), sample))
        return
    }

    val request = Json.decodeFromString<GenerationRequest>(inputFile.readText())
    println("Generating layouts for: ${request.inventory}")

    val solver = Solver(request.inventory, request.maxSolutions)
    val startTime = System.currentTimeMillis()
    val solutions = solver.solve()
    val endTime = System.currentTimeMillis()

    println("Found ${solutions.size} solutions in ${endTime - startTime}ms")
    if (solutions.size < request.maxSolutions) {
        println("Warning: Could only generate ${solutions.size} unique solutions (requested ${request.maxSolutions}).")
    }

    val outputDir = File(request.outputDir)
    if (!outputDir.exists()) outputDir.mkdirs()

    val renderer = SvgRenderer()
    solutions.forEachIndexed { index, solution ->
        val svg = renderer.render(solution)
        val file = File(outputDir, "solution_${index + 1}.svg")
        file.writeText(svg)
        println("Saved ${file.path}")
    }
}
