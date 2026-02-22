package org.example.legotrack.renderer

import kotlinx.serialization.json.*
import org.example.legotrack.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class LayoutJsonSerializerTest {

    private val serializer = LayoutJsonSerializer()

    @Test
    fun testGroupingStraights() {
        val pieces = listOf(
            PlacedPiece(TrackLibrary.STRAIGHT, Pose(0.0, 0.0, 0.0), 0),
            PlacedPiece(TrackLibrary.STRAIGHT, Pose(16.0, 0.0, 0.0), 0)
        )
        val json = serializer.serialize(pieces)

        assertEquals(1, json.size)
        val straight = json[0].jsonObject["straight"]?.jsonPrimitive?.int
        assertEquals(2, straight)
    }

    @Test
    fun testGroupingCurves() {
        val p1 = PlacedPiece(TrackLibrary.CURVE_R40, Pose(0.0, 0.0, 0.0), 0)
        val p2 = PlacedPiece(TrackLibrary.CURVE_R40, p1.exitPose, 0)
        val pieces = listOf(p1, p2)

        val json = serializer.serialize(pieces)

        assertEquals(1, json.size)
        val turn = json[0].jsonObject["turn"]?.jsonObject
        assertEquals(45.0, turn?.get("deg")?.jsonPrimitive?.double)
        assertEquals("right", turn?.get("direction")?.jsonPrimitive?.content)
    }

    @Test
    fun testSidingNesting() {
        // Main loop: P1, Switch, P2, Merge, P3
        // Siding: S1 (starts at Switch branch exit, ends at Merge branch entry)

        val p1 = PlacedPiece(TrackLibrary.STRAIGHT, Pose(0.0, 0.0, 0.0), 0)
        val sw = PlacedPiece(TrackLibrary.SWITCH_LEFT, p1.exitPose, 0) // Exit 0 is straight
        val p2 = PlacedPiece(TrackLibrary.STRAIGHT, sw.allConnectorPoses[0], 0)

        // Siding starting at branch exit (Exit 1)
        val s1 = PlacedPiece(TrackLibrary.STRAIGHT, sw.allConnectorPoses[1], 0)

        // Merge piece
        val mr = PlacedPiece(TrackLibrary.SWITCH_RIGHT_REV_STRAIGHT, p2.exitPose, 0)
        // Note: In real layout, s1.exitPose should match mr branch entry, but for this test we just care about processing order

        val pieces = listOf(p1, sw, p2, mr, s1)

        val json = serializer.serialize(pieces)

        // Expected JSON structure:
        // [
        //   { "straight": 1 },
        //   { "switch_left": { "orientation": "switch", "branch": { "content": [{ "straight": 1 }] }, ... } },
        //   { "straight": 1 },
        //   { "switch_right": { "orientation": "merge", ... } }
        // ]

        assertEquals(4, json.size)

        val swJson = json[1].jsonObject["switch_left"]?.jsonObject
        assertEquals("switch", swJson?.get("orientation")?.jsonPrimitive?.content)

        val branchContent = swJson?.get("branch")?.jsonObject?.get("content")?.jsonArray
        assertEquals(1, branchContent?.size)
        assertEquals(1, branchContent?.get(0)?.jsonObject?.get("straight")?.jsonPrimitive?.int)

        val mrJson = json[3].jsonObject["switch_right"]?.jsonObject
        assertEquals("merge", mrJson?.get("orientation")?.jsonPrimitive?.content)
    }
}
