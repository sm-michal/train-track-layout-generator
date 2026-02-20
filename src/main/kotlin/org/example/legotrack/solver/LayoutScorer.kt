package org.example.legotrack.solver

import org.example.legotrack.model.PlacedPiece
import org.example.legotrack.model.TrackType
import kotlin.math.*

data class LayoutFeatures(
    val longStraights: Int = 0,
    val longTurns45: Int = 0,
    val longTurns90: Int = 0,
    val longTurns180: Int = 0,
    val zigZags: Int = 0,
    val isLShape: Boolean = false,
    val isUShape: Boolean = false,
    val hasSiding: Boolean = false,
    val hasDeadEnd: Boolean = false,
    val hasMultiLoop: Boolean = false
)

data class IncrementalFeatures(
    val totalRotation: Double = 0.0,
    val totalAbsRotation: Double = 0.0,
    val currentStraight: Int = 0,
    val currentTurnAngle: Double = 0.0,
    val lastTurnDir: Int = 0,
    val longStraights: Int = 0,
    val longTurns45: Int = 0,
    val longTurns90: Int = 0,
    val longTurns180: Int = 0,
    val zigZags: Int = 0,
    val switchCount: Int = 0
)

class LayoutScorer(private val globalFeatureUsage: Map<String, Int>) {

    fun extractFeatures(path: List<PlacedPiece>): LayoutFeatures {
        var longStraights = 0
        var currentStraight = 0

        var longTurns45 = 0
        var longTurns90 = 0
        var longTurns180 = 0
        var currentTurnAngle = 0.0

        var zigZags = 0
        var lastTurnDir = 0 // 1 for right, -1 for left, 0 for none

        // Pattern detection for L/U shapes
        val turns = mutableListOf<Double>() // List of continuous turn angles

        for (piece in path) {
            val type = piece.definition.type
            val angle = piece.definition.exits.getOrNull(piece.chosenExitIndex)?.dRotation ?: 0.0

            // Straights
            if (type == TrackType.STRAIGHT || (type == TrackType.SWITCH && abs(angle) < 0.1)) {
                currentStraight += if (type == TrackType.SWITCH) 2 else 1

                if (abs(currentTurnAngle) > 0.1) {
                    turns.add(currentTurnAngle)
                    val absA = abs(currentTurnAngle)
                    if (abs(absA - 45.0) < 1.0) longTurns45++
                    else if (abs(absA - 90.0) < 1.0) longTurns90++
                    else if (abs(absA - 180.0) < 1.0) longTurns180++
                    currentTurnAngle = 0.0
                }
            } else if (type == TrackType.CURVE || (type == TrackType.SWITCH && abs(angle) > 0.1)) {
                if (currentStraight >= 4) {
                    longStraights++
                }
                currentStraight = 0

                val dir = if (angle > 0) 1 else -1
                if (lastTurnDir != 0 && lastTurnDir != dir) {
                    zigZags++
                    turns.add(currentTurnAngle)
                    currentTurnAngle = 0.0
                }
                currentTurnAngle += angle
                lastTurnDir = dir
            }
        }
        // Final pieces
        if (currentStraight >= 4) longStraights++
        if (abs(currentTurnAngle) > 0.1) {
            turns.add(currentTurnAngle)
            val absA = abs(currentTurnAngle)
            if (abs(absA - 45.0) < 1.0) longTurns45++
            else if (abs(absA - 90.0) < 1.0) longTurns90++
            else if (abs(absA - 180.0) < 1.0) longTurns180++
        }

        // L-shape and U-shape detection based on turn directionality and total rotation
        var isLShape = false
        var isUShape = false

        val totalRotation = turns.sum()
        val totalAbsRotation = turns.sumOf { abs(it) }

        if (abs(totalRotation) > 1.0) { // Avoid division by zero
            val ratio = totalAbsRotation / abs(totalRotation)
            // Use a bit of tolerance for the ratio
            if (abs(ratio - 1.5) < 0.1) isLShape = true
            if (abs(ratio - 2.0) < 0.1) isUShape = true
        }

        val hasSiding = path.any { it.definition.type == TrackType.SWITCH && !it.isDeadEnd && it.deadEndExits.isEmpty() }
        val hasDeadEnd = path.any { it.isDeadEnd || it.deadEndExits.isNotEmpty() }

        return LayoutFeatures(
            longStraights = longStraights,
            longTurns45 = longTurns45,
            longTurns90 = longTurns90,
            longTurns180 = longTurns180,
            zigZags = zigZags,
            isLShape = isLShape,
            isUShape = isUShape,
            hasSiding = hasSiding,
            hasDeadEnd = hasDeadEnd,
            hasMultiLoop = hasSiding // Simplified for now
        )
    }

    fun calculateScore(features: LayoutFeatures): Double {
        var score = 0.0

        // Rewards (prioritized weights)
        score += (if (features.hasMultiLoop) 1.0 else 0.0) * 1000.0
        score += (if (features.hasSiding) 1.0 else 0.0) * 500.0
        score += features.longStraights.toDouble() * 100.0
        score += features.longTurns180.toDouble() * 80.0
        score += features.longTurns90.toDouble() * 80.0
        score += features.longTurns45.toDouble() * 80.0
        score += (if (features.isUShape) 1.0 else 0.0) * 50.0
        score += (if (features.isLShape) 1.0 else 0.0) * 30.0
        score += (if (features.hasDeadEnd) 1.0 else 0.0) * 10.0

        // Penalties
        score -= features.zigZags.toDouble() * 50.0

        // Diversity (Strategy B)
        if (features.isLShape) score -= globalFeatureUsage.getOrDefault("l_shape", 0) * 200.0
        if (features.isUShape) score -= globalFeatureUsage.getOrDefault("u_shape", 0) * 300.0
        if (features.hasSiding) score -= globalFeatureUsage.getOrDefault("siding", 0) * 400.0

        return score
    }

    fun updateIncremental(feat: IncrementalFeatures, piece: PlacedPiece): IncrementalFeatures {
        var tr = feat.totalRotation
        var tar = feat.totalAbsRotation
        var cs = feat.currentStraight
        var cta = feat.currentTurnAngle
        var ltd = feat.lastTurnDir
        var ls = feat.longStraights
        var lt45 = feat.longTurns45
        var lt90 = feat.longTurns90
        var lt180 = feat.longTurns180
        var zz = feat.zigZags
        var swc = feat.switchCount + if (piece.definition.type == TrackType.SWITCH) 1 else 0

        val type = piece.definition.type
        val angle = piece.definition.exits.getOrNull(piece.chosenExitIndex)?.dRotation ?: 0.0

        if (type == TrackType.STRAIGHT || (type == TrackType.SWITCH && abs(angle) < 0.1)) {
            cs += if (type == TrackType.SWITCH) 2 else 1
            if (abs(cta) > 0.1) {
                tr += cta
                tar += abs(cta)
                val absA = abs(cta)
                if (abs(absA - 45.0) < 1.0) lt45++
                else if (abs(absA - 90.0) < 1.0) lt90++
                else if (abs(absA - 180.0) < 1.0) lt180++
                cta = 0.0
            }
        } else if (type == TrackType.CURVE || (type == TrackType.SWITCH && abs(angle) > 0.1)) {
            if (cs >= 4) ls++
            cs = 0
            val dir = if (angle > 0) 1 else -1
            if (ltd != 0 && ltd != dir) {
                zz++
                tr += cta
                tar += abs(cta)
                cta = 0.0
            }
            cta += angle
            ltd = dir
        }

        return IncrementalFeatures(tr, tar, cs, cta, ltd, ls, lt45, lt90, lt180, zz, swc)
    }

    fun IncrementalFeatures.toLayoutFeatures(): LayoutFeatures {
        val finalTr = totalRotation + currentTurnAngle
        val finalTar = totalAbsRotation + abs(currentTurnAngle)
        val ratio = if (abs(finalTr) > 1.0) finalTar / abs(finalTr) else 1.0
        return LayoutFeatures(
            longStraights = longStraights,
            longTurns45 = longTurns45,
            longTurns90 = longTurns90,
            longTurns180 = longTurns180,
            zigZags = zigZags,
            isLShape = abs(ratio - 1.5) < 0.1,
            isUShape = abs(ratio - 2.0) < 0.1,
            hasSiding = switchCount > 0,
            hasDeadEnd = false,
            hasMultiLoop = switchCount >= 2
        )
    }
}
