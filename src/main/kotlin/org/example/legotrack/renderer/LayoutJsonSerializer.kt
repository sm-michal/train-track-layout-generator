package org.example.legotrack.renderer

import kotlinx.serialization.json.*
import org.example.legotrack.model.*
import kotlin.math.*

class LayoutJsonSerializer {

    private var pathIdCounter = 1

    fun serialize(pieces: List<PlacedPiece>): JsonArray {
        pathIdCounter = 1
        val processed = mutableSetOf<PlacedPiece>()
        val pathIds = mutableMapOf<Pose, String>()

        return buildLayoutArray(pieces, processed, pathIds)
    }

    private fun buildLayoutArray(
        pieces: List<PlacedPiece>,
        processed: MutableSet<PlacedPiece>,
        pathIds: MutableMap<Pose, String>
    ): JsonArray {
        val result = mutableListOf<JsonElement>()
        var i = 0
        while (i < pieces.size) {
            val piece = pieces[i]
            if (piece in processed) {
                i++
                continue
            }

            when (piece.definition.type) {
                TrackType.STRAIGHT -> {
                    val count = countConsecutive(pieces, i, processed) { it.definition.type == TrackType.STRAIGHT }
                    result.add(buildJsonObject {
                        put("straight", count)
                    })
                    repeat(count) { processed.add(pieces[i + it]) }
                    i += count
                }
                TrackType.CURVE -> {
                    val isRight = piece.definition.arcAngle > 0
                    val count = countConsecutive(pieces, i, processed) {
                        it.definition.type == TrackType.CURVE && (it.definition.arcAngle > 0) == isRight
                    }
                    result.add(buildJsonObject {
                        put("turn", buildJsonObject {
                            put("rad", 40)
                            put("deg", count * 22.5)
                            put("direction", if (isRight) "right" else "left")
                        })
                    })
                    repeat(count) { processed.add(pieces[i + it]) }
                    i += count
                }
                TrackType.SWITCH -> {
                    val isMerge = piece.definition.id.contains("rev")
                    val isLeft = piece.definition.id.contains("left")
                    val key = if (isLeft) "switch_left" else "switch_right"

                    if (!isMerge) {
                        val pathIdBase = "path_${pathIdCounter++}"
                        val straightId = "${pathIdBase}_s"
                        val branchId = "${pathIdBase}_b"

                        // Register path IDs for the exits
                        // Exit 0 is straight, Exit 1 is branch
                        val straightExitPose = piece.allConnectorPoses[0]
                        val branchExitPose = piece.allConnectorPoses[1]
                        pathIds[straightExitPose] = straightId
                        pathIds[branchExitPose] = branchId

                        val chosenExit = piece.chosenExitIndex
                        val otherExit = 1 - chosenExit
                        val otherExitPose = piece.allConnectorPoses[otherExit]

                        val siding = findSiding(otherExitPose, pieces, processed)
                        val sidingContent = if (siding.isNotEmpty()) {
                            buildLayoutArray(siding, processed, pathIds)
                        } else null

                        result.add(buildJsonObject {
                            put(key, buildJsonObject {
                                put("straight", buildJsonObject {
                                    put("id", straightId)
                                    if (chosenExit == 1 && sidingContent != null) {
                                        put("content", sidingContent)
                                    }
                                })
                                put("branch", buildJsonObject {
                                    put("id", branchId)
                                    if (chosenExit == 0 && sidingContent != null) {
                                        put("content", sidingContent)
                                    }
                                })
                                put("orientation", "switch")
                            })
                        })
                        processed.add(piece)
                        i++
                    } else {
                        // Merge
                        // We need to find the IDs for straight and branch entries
                        // TrackLibrary definitions for rev:
                        // REV_STRAIGHT: Entry is C2 (Straight), other is C3 (Branch)
                        // REV_BRANCH: Entry is C3 (Branch), other is C2 (Straight)

                        val isRevStraight = piece.definition.id.contains("rev_s")
                        val entryPose = piece.pose

                        // Find the other entry connector pose
                        // In TrackLibrary, allConnectors for rev pieces:
                        // [0] = Exit, [1] = Other Entry, [2] = Entry
                        val otherEntryPose = piece.allConnectorPoses[1]

                        // The IDs should have been registered by the corresponding "switch"
                        // But wait, the path IDs are stored at the EXIT of the previous switch.
                        // The entry of this merge should match those exit poses.

                        val straightEntryPose = if (isRevStraight) entryPose else otherEntryPose
                        val branchEntryPose = if (isRevStraight) otherEntryPose else entryPose

                        val straightId = findPathId(straightEntryPose, pathIds)
                        val branchId = findPathId(branchEntryPose, pathIds)

                        result.add(buildJsonObject {
                            put(key, buildJsonObject {
                                put("straight", buildJsonObject {
                                    put("id", straightId ?: "unknown_s")
                                })
                                put("branch", buildJsonObject {
                                    put("id", branchId ?: "unknown_b")
                                })
                                put("orientation", "merge")
                            })
                        })
                        processed.add(piece)
                        i++
                    }
                }
            }
        }
        return JsonArray(result)
    }

    private fun countConsecutive(
        pieces: List<PlacedPiece>,
        start: Int,
        processed: Set<PlacedPiece>,
        predicate: (PlacedPiece) -> Boolean
    ): Int {
        var count = 0
        while (start + count < pieces.size) {
            val p = pieces[start + count]
            if (p in processed || !predicate(p)) break
            count++
        }
        return count
    }

    private fun findSiding(
        startPose: Pose,
        allPieces: List<PlacedPiece>,
        processed: Set<PlacedPiece>
    ): List<PlacedPiece> {
        val siding = mutableListOf<PlacedPiece>()
        var currentPose = startPose

        // Simple search: find an unprocessed piece that starts at currentPose
        // Since sidings are chains, there should be at most one.
        while (true) {
            val next = allPieces.find { it !in processed && it !in siding && it.pose.distanceTo(currentPose) < 0.1 }
                ?: break

            // If we hit a merge piece, it should be at the top level, not inside siding content
            if (next.definition.id.contains("rev")) break

            siding.add(next)
            currentPose = next.exitPose
            // If it's a regular switch (not rev), it shouldn't really happen in a siding with current solver,
            // but if it does, we stop here to avoid complex nesting for now.
            if (next.definition.type == TrackType.SWITCH) break
        }
        return siding
    }

    private fun findPathId(pose: Pose, pathIds: Map<Pose, String>): String? {
        return pathIds.entries.find { it.key.distanceTo(pose) < 0.1 }?.value
    }
}
