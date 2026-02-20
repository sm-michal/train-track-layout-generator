package org.example.legotrack.solver

import kotlinx.serialization.json.*
import org.example.legotrack.model.*
import org.example.legotrack.renderer.SvgRenderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import kotlin.math.*

class ManualLayoutTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    class ManualLayoutBuilder {
        val pieces = mutableListOf<PlacedPiece>()
        var currentPose = Pose(0.0, 0.0, 0.0)

        // Heads of branches that were created but not yet continued
        private val pendingHeads = mutableListOf<Pose>()

        fun buildFromJson(element: JsonElement) {
            when (element) {
                is JsonArray -> element.forEach { buildFromJson(it) }
                is JsonObject -> {
                    for ((key, value) in element) {
                        when (key) {
                            "turn" -> addTurn(value.jsonObject)
                            "straight" -> addStraight(value)
                            "switch_left" -> addSwitch(TrackLibrary.SWITCH_LEFT, value.jsonObject)
                            "switch_right" -> addSwitch(TrackLibrary.SWITCH_RIGHT, value.jsonObject)
                        }
                    }
                }
                else -> {}
            }
        }

        private fun addStraight(value: JsonElement) {
            val count = when (value) {
                is JsonPrimitive -> value.int
                is JsonObject -> value["count"]?.jsonPrimitive?.int ?: 1
                else -> 1
            }
            repeat(count) {
                val piece = PlacedPiece(TrackLibrary.STRAIGHT, currentPose, 0)
                pieces.add(piece)
                currentPose = piece.exitPose
            }
        }

        private fun addTurn(obj: JsonObject) {
            val deg = obj["deg"]?.jsonPrimitive?.double ?: 0.0
            val direction = obj["direction"]?.jsonPrimitive?.content ?: "right"
            val rad = obj["rad"]?.jsonPrimitive?.double ?: 40.0

            val numCurves = (abs(deg) / 22.5).roundToInt()
            val isRight = direction.lowercase() == "right"

            // In our library, CURVE_R40 has arcAngle 22.5 (Right)
            // and CURVE_R40_RIGHT has arcAngle -22.5 (Left)
            val def = if (isRight) TrackLibrary.CURVE_R40 else TrackLibrary.CURVE_R40_RIGHT

            repeat(numCurves) {
                val piece = PlacedPiece(def, currentPose, 0)
                pieces.add(piece)
                currentPose = piece.exitPose
            }
        }

        private fun addSwitch(def: TrackPieceDefinition, obj: JsonObject) {
            val orientation = obj["orientation"]?.jsonPrimitive?.content ?: "switch"

            if (orientation == "switch") {
                // Diverge
                val sw = PlacedPiece(def, currentPose, 0)
                pieces.add(sw)

                val leftPath = obj["left"]?.jsonArray
                val rightPath = obj["right"]?.jsonArray

                val savedPose = currentPose

                // We'll follow one path as "main" and store the other as pending
                // For a switch piece: exit 0 is straight, exit 1 is branch
                // Based on LEGO, switch_left branches left.
                // In our lib, SWITCH_LEFT branches with +22.5 (Right turn in SVG).

                // Let's assume 'right' path goes to exit 0 (straight) and 'left' to exit 1 (branch)
                // or vice-versa depending on piece ID.

                val branchExitIdx = if (def.id.contains("left")) 1 else 1
                val straightExitIdx = 0

                // Process right branch
                currentPose = sw.allConnectorPoses[straightExitIdx]
                if (rightPath != null) buildFromJson(rightPath)
                val endR = currentPose

                // Process left branch
                currentPose = sw.allConnectorPoses[branchExitIdx]
                if (leftPath != null) buildFromJson(leftPath)
                val endL = currentPose

                // Pending heads for merge
                pendingHeads.add(endR)
                pendingHeads.add(endL)

                // Continue from one of them (e.g. the last one processed)
                currentPose = endL
            } else {
                // Merge
                // Find pieces to use for merge.
                // If it's switch_right merge, we want to merge a branch into a straight.
                // We'll use the reverse definitions.
                val revDef = when (def.id) {
                    "switch_left" -> TrackLibrary.SWITCH_LEFT_REV_STRAIGHT
                    "switch_right" -> TrackLibrary.SWITCH_RIGHT_REV_STRAIGHT
                    else -> TrackLibrary.SWITCH_LEFT_REV_STRAIGHT
                }

                // Place it at currentPose
                val sw = PlacedPiece(revDef, currentPose, 0)
                pieces.add(sw)
                currentPose = sw.exitPose

                // In a real implementation we'd verify that the other branch end matches sw.allConnectors[1]
                if (pendingHeads.isNotEmpty()) {
                    // Remove the head we just used
                    // This is simplified
                }
            }
        }
    }

    @Test
    fun testCircleClosure() {
        val jsonText = """
        [
            { "turn": { "deg": 360, "direction": "right" } }
        ]
        """.trimIndent()
        val element = json.parseToJsonElement(jsonText)
        val builder = ManualLayoutBuilder()
        builder.buildFromJson(element)
        val pieces = builder.pieces

        val isClosed = isLayoutClosed(pieces)
        println("Circle is closed: ${isClosed}")
        assertTrue(isClosed, "Circle layout should be closed")
    }

    @Test
    fun testUserLayout() {
        val jsonText = javaClass.classLoader.getResource("user_layout.json")?.readText()
            ?: throw IllegalStateException("user_layout.json not found in resources")

        val element = json.parseToJsonElement(jsonText)
        val builder = ManualLayoutBuilder()
        builder.buildFromJson(element)
        val pieces = builder.pieces

        // Visualize
        val renderer = SvgRenderer()
        val svg = renderer.render(pieces)
        val outputFile = File("output/manual_layout.svg")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(svg)
        println("Saved visualization to ${outputFile.absolutePath}")

        // Score
        val scorer = LayoutScorer(emptyMap())
        val features = scorer.extractFeatures(pieces)
        val breakdown = scorer.getScoreBreakdown(features)

        println("\nManual Layout Score Breakdown:")
        println("Total Score: ${breakdown.totalScore}")
        breakdown.components.filter { it.value != 0.0 }.forEach { (name, value) ->
            println("  $name: $value")
        }

        // Validate loop
        val isClosed = isLayoutClosed(pieces)
        println("\nIs layout closed: ${isClosed}")

        // Assert that it has at least one loop
        assertTrue(isClosed, "Layout should form at least one closed loop")
    }

    private fun normalizeAngle(angle: Double): Double {
        var a = angle % 360.0
        if (a < 0) a += 360.0
        if (a >= 360.0 - 0.01) a = 0.0
        return a
    }

    private fun isLayoutClosed(pieces: List<PlacedPiece>): Boolean {
        if (pieces.isEmpty()) return false

        // Build a graph where nodes are (pieceIndex, connectorIndex)
        // and edges exist between connectors of the same piece, and between matching poses.

        val adj = mutableMapOf<Pair<Int, Int>, MutableList<Pair<Int, Int>>>()

        // 1. Connectors within the same piece
        for (i in pieces.indices) {
            val piece = pieces[i]
            val numConnectors = piece.definition.allConnectors.size
            for (c1 in 0 until numConnectors) {
                for (c2 in 0 until numConnectors) {
                    if (c1 != c2) {
                        adj.getOrPut(i to c1) { mutableListOf() }.add(i to c2)
                    }
                }
            }
        }

        // 2. Connections between different pieces
        val allConnectorNodes = pieces.indices.flatMap { i ->
            pieces[i].definition.allConnectors.indices.map { c -> i to c }
        }

        for (i in allConnectorNodes.indices) {
            for (j in i + 1 until allConnectorNodes.size) {
                val n1 = allConnectorNodes[i]
                val n2 = allConnectorNodes[j]
                val p1 = pieces[n1.first].allConnectorPoses[n1.second]
                val p2 = pieces[n2.first].allConnectorPoses[n2.second]

                val dist = p1.distanceTo(p2)
                val angleDist = abs(normalizeAngle(p1.rotation) - normalizeAngle(p2.rotation))
                val angleMatch = angleDist < 0.2 || abs(angleDist - 180.0) < 0.2

                if (dist < 0.2 && angleMatch) {
                    adj.getOrPut(n1) { mutableListOf() }.add(n2)
                    adj.getOrPut(n2) { mutableListOf() }.add(n1)
                }
            }
        }

        // 3. Find cycles using DFS
        val visited = mutableSetOf<Pair<Int, Int>>()

        fun hasCycle(curr: Pair<Int, Int>, parent: Pair<Int, Int>?, path: MutableSet<Pair<Int, Int>>): Boolean {
            visited.add(curr)
            path.add(curr)

            for (next in adj[curr] ?: emptyList()) {
                if (next == parent) continue
                if (next in path) {
                    // Check if the cycle involves at least two different pieces
                    val piecesInCycle = path.map { it.first }.toSet() + next.first
                    if (piecesInCycle.size > 2) return true
                    continue
                }
                if (next !in visited) {
                    if (hasCycle(next, curr, path)) return true
                }
            }

            path.remove(curr)
            return false
        }

        for (i in pieces.indices) {
            for (c in pieces[i].definition.allConnectors.indices) {
                val startNode = i to c
                if (startNode !in visited) {
                    if (hasCycle(startNode, null, mutableSetOf())) return true
                }
            }
        }

        return false
    }
}
