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

        // Poses at the end of named paths (from switches)
        private val namedPathEnds = mutableMapOf<String, Pose>()

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

            // In our library, CURVE_R40_RIGHT has arcAngle 22.5 (Right)
            // and CURVE_R40_LEFT has arcAngle -22.5 (Left)
            val def = if (isRight) TrackLibrary.CURVE_R40_RIGHT else TrackLibrary.CURVE_R40_LEFT

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

                val branchObj = obj["branch"]
                val straightObj = obj["straight"]

                // Straight path is always exit 0 in our library
                currentPose = sw.allConnectorPoses[0]
                if (straightObj is JsonObject) {
                    straightObj["tracks"]?.let { buildFromJson(it) }
                    straightObj["id"]?.jsonPrimitive?.content?.let { namedPathEnds[it] = currentPose }
                } else if (straightObj is JsonArray) {
                    buildFromJson(straightObj)
                }

                // Branch path is always exit 1 in our library
                currentPose = sw.allConnectorPoses[1]
                if (branchObj is JsonObject) {
                    branchObj["tracks"]?.let { buildFromJson(it) }
                    branchObj["id"]?.jsonPrimitive?.content?.let { namedPathEnds[it] = currentPose }
                } else if (branchObj is JsonArray) {
                    buildFromJson(branchObj)
                }

                // By default, continue from the straight path end for the next pieces in the main JSON array
                // unless the user specifies otherwise. For now, let's stick to straight as the main continuation.
                val straightId = (straightObj as? JsonObject)?.get("id")?.jsonPrimitive?.content
                currentPose = if (straightId != null) namedPathEnds[straightId]!! else sw.allConnectorPoses[0]
            } else {
                // Merge
                val branchId = obj["branch"]?.jsonPrimitive?.content
                val straightId = obj["straight"]?.jsonPrimitive?.content

                val poseBranch = branchId?.let { namedPathEnds[it] }
                val poseStraight = straightId?.let { namedPathEnds[it] }

                // Check which path we are continuing from
                val matchesBranch = poseBranch != null && currentPose.distanceTo(poseBranch) < 0.1

                val revDef = if (matchesBranch) {
                    when (def.id) {
                        "switch_left" -> TrackLibrary.SWITCH_LEFT_REV_BRANCH
                        "switch_right" -> TrackLibrary.SWITCH_RIGHT_REV_BRANCH
                        else -> TrackLibrary.SWITCH_LEFT_REV_BRANCH
                    }
                } else {
                    when (def.id) {
                        "switch_left" -> TrackLibrary.SWITCH_LEFT_REV_STRAIGHT
                        "switch_right" -> TrackLibrary.SWITCH_RIGHT_REV_STRAIGHT
                        else -> TrackLibrary.SWITCH_LEFT_REV_STRAIGHT
                    }
                }

                val sw = PlacedPiece(revDef, currentPose, 0)
                pieces.add(sw)

                // Verification of the merging connection
                val otherPose = if (matchesBranch) poseStraight else poseBranch
                if (otherPose != null) {
                    val mergeConnectorIdx = if (matchesBranch) 1 else 1 // C2 for REV_BRANCH, C3 for REV_STRAIGHT. Both are index 1 in allConnectors list.
                    val mergePose = sw.allConnectorPoses[mergeConnectorIdx]
                    val dist = otherPose.distanceTo(mergePose)
                    val angleDist = abs(otherPose.angleDistanceTo(mergePose) - 180.0) // Should be opposite
                    if (dist > 0.5 || angleDist > 1.0) {
                        println("Warning: Merge connection mismatch at ${sw.definition.id}. Dist: $dist, AngleDist: $angleDist")
                    } else {
                        println("Verified merge connection for ${sw.definition.id}")
                    }
                }

                currentPose = sw.exitPose
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
