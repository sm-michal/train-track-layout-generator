package org.example.legotrack.solver

import org.example.legotrack.model.PlacedPiece
import org.example.legotrack.model.TrackType
import kotlin.math.*

data class LayoutFeatures(
    val longStraights: Int = 0,
    val totalStraightPieces: Int = 0,
    val longTurns45: Int = 0,
    val longTurns90: Int = 0,
    val longTurns135: Int = 0,
    val longTurns180: Int = 0,
    val longTurns225: Int = 0,
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
    val totalStraightPieces: Int = 0,
    val longTurns45: Int = 0,
    val longTurns90: Int = 0,
    val longTurns135: Int = 0,
    val longTurns180: Int = 0,
    val longTurns225: Int = 0,
    val zigZags: Int = 0,
    val switchCount: Int = 0
)

class LayoutScorer(private val globalFeatureUsage: Map<String, Int>) {

    fun extractFeatures(path: List<PlacedPiece>): LayoutFeatures {
        var longStraights = 0
        var totalStraightPieces = 0
        var currentStraight = 0

        var longTurns45 = 0
        var longTurns90 = 0
        var longTurns135 = 0
        var longTurns180 = 0
        var longTurns225 = 0
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
                    else if (abs(absA - 135.0) < 1.0) longTurns135++
                    else if (abs(absA - 180.0) < 1.0) longTurns180++
                    else if (abs(absA - 225.0) < 1.0) longTurns225++
                    currentTurnAngle = 0.0
                }
            } else if (type == TrackType.CURVE || (type == TrackType.SWITCH && abs(angle) > 0.1)) {
                if (currentStraight >= 4) {
                    longStraights++
                    totalStraightPieces += currentStraight
                }
                currentStraight = 0

                val dir = if (angle > 0) 1 else -1
                if (lastTurnDir != 0 && lastTurnDir != dir) {
                    zigZags++
                    turns.add(currentTurnAngle)
                    val absA = abs(currentTurnAngle)
                    if (abs(absA - 45.0) < 1.0) longTurns45++
                    else if (abs(absA - 90.0) < 1.0) longTurns90++
                    else if (abs(absA - 135.0) < 1.0) longTurns135++
                    else if (abs(absA - 180.0) < 1.0) longTurns180++
                    else if (abs(absA - 225.0) < 1.0) longTurns225++
                    currentTurnAngle = 0.0
                }
                currentTurnAngle += angle
                lastTurnDir = dir
            }
        }
        // Final pieces
        if (currentStraight >= 4) {
            longStraights++
            totalStraightPieces += currentStraight
        }
        if (abs(currentTurnAngle) > 0.1) {
            turns.add(currentTurnAngle)
            val absA = abs(currentTurnAngle)
            if (abs(absA - 45.0) < 1.0) longTurns45++
            else if (abs(absA - 90.0) < 1.0) longTurns90++
            else if (abs(absA - 135.0) < 1.0) longTurns135++
            else if (abs(absA - 180.0) < 1.0) longTurns180++
            else if (abs(absA - 225.0) < 1.0) longTurns225++
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
            totalStraightPieces = totalStraightPieces,
            longTurns45 = longTurns45,
            longTurns90 = longTurns90,
            longTurns135 = longTurns135,
            longTurns180 = longTurns180,
            longTurns225 = longTurns225,
            zigZags = zigZags,
            isLShape = isLShape,
            isUShape = isUShape,
            hasSiding = hasSiding,
            hasDeadEnd = hasDeadEnd,
            hasMultiLoop = hasSiding // Simplified for now
        )
    }

    data class ScoreBreakdown(
        val totalScore: Double,
        val components: Map<String, Double>
    )

    fun calculateScore(features: LayoutFeatures): Double {
        return getScoreBreakdown(features).totalScore
    }

    fun getScoreBreakdown(features: LayoutFeatures): ScoreBreakdown {
        val components = mutableMapOf<String, Double>()

        // Rewards (prioritized weights)
        components["multi_loop"] = (if (features.hasMultiLoop) 1.0 else 0.0) * 1000.0
        components["siding"] = (if (features.hasSiding) 1.0 else 0.0) * 500.0

        // Balanced straight scoring: count * 100 + pieces * 15
        components["long_straights_count"] = features.longStraights.toDouble() * 100.0
        components["long_straights_pieces"] = features.totalStraightPieces.toDouble() * 15.0

        components["long_turns_225"] = features.longTurns225.toDouble() * 80.0
        components["long_turns_180"] = features.longTurns180.toDouble() * 80.0
        components["long_turns_135"] = features.longTurns135.toDouble() * 80.0
        components["long_turns_90"] = features.longTurns90.toDouble() * 80.0
        components["long_turns_45"] = features.longTurns45.toDouble() * 80.0

        components["u_shape"] = (if (features.isUShape) 1.0 else 0.0) * 50.0
        components["l_shape"] = (if (features.isLShape) 1.0 else 0.0) * 30.0
        components["dead_end"] = (if (features.hasDeadEnd) 1.0 else 0.0) * 10.0

        // Penalties
        components["zig_zags"] = -features.zigZags.toDouble() * 50.0

        // Diversity (Strategy B)
        if (features.isLShape) {
            components["diversity_l_shape"] = -globalFeatureUsage.getOrDefault("l_shape", 0) * 200.0
        }
        if (features.isUShape) {
            components["diversity_u_shape"] = -globalFeatureUsage.getOrDefault("u_shape", 0) * 300.0
        }
        if (features.hasSiding) {
            components["diversity_siding"] = -globalFeatureUsage.getOrDefault("siding", 0) * 400.0
        }

        return ScoreBreakdown(components.values.sum(), components)
    }

    fun updateIncremental(feat: IncrementalFeatures, piece: PlacedPiece): IncrementalFeatures {
        var tr = feat.totalRotation
        var tar = feat.totalAbsRotation
        var cs = feat.currentStraight
        var cta = feat.currentTurnAngle
        var ltd = feat.lastTurnDir
        var ls = feat.longStraights
        var tsp = feat.totalStraightPieces
        var lt45 = feat.longTurns45
        var lt90 = feat.longTurns90
        var lt135 = feat.longTurns135
        var lt180 = feat.longTurns180
        var lt225 = feat.longTurns225
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
                else if (abs(absA - 135.0) < 1.0) lt135++
                else if (abs(absA - 180.0) < 1.0) lt180++
                else if (abs(absA - 225.0) < 1.0) lt225++
                cta = 0.0
            }
        } else if (type == TrackType.CURVE || (type == TrackType.SWITCH && abs(angle) > 0.1)) {
            if (cs >= 4) {
                ls++
                tsp += cs
            }
            cs = 0
            val dir = if (angle > 0) 1 else -1
            if (ltd != 0 && ltd != dir) {
                zz++
                tr += cta
                tar += abs(cta)
                val absA = abs(cta)
                if (abs(absA - 45.0) < 1.0) lt45++
                else if (abs(absA - 90.0) < 1.0) lt90++
                else if (abs(absA - 135.0) < 1.0) lt135++
                else if (abs(absA - 180.0) < 1.0) lt180++
                else if (abs(absA - 225.0) < 1.0) lt225++
                cta = 0.0
            }
            cta += angle
            ltd = dir
        }

        return IncrementalFeatures(tr, tar, cs, cta, ltd, ls, tsp, lt45, lt90, lt135, lt180, lt225, zz, swc)
    }

    fun IncrementalFeatures.toLayoutFeatures(): LayoutFeatures {
        val finalTr = totalRotation + currentTurnAngle
        val finalTar = totalAbsRotation + abs(currentTurnAngle)
        val ratio = if (abs(finalTr) > 1.0) finalTar / abs(finalTr) else 1.0

        var lt45 = longTurns45
        var lt90 = longTurns90
        var lt135 = longTurns135
        var lt180 = longTurns180
        var lt225 = longTurns225

        if (abs(currentTurnAngle) > 0.1) {
            val absA = abs(currentTurnAngle)
            if (abs(absA - 45.0) < 1.0) lt45++
            else if (abs(absA - 90.0) < 1.0) lt90++
            else if (abs(absA - 135.0) < 1.0) lt135++
            else if (abs(absA - 180.0) < 1.0) lt180++
            else if (abs(absA - 225.0) < 1.0) lt225++
        }

        return LayoutFeatures(
            longStraights = longStraights + if (currentStraight >= 4) 1 else 0,
            totalStraightPieces = totalStraightPieces + if (currentStraight >= 4) currentStraight else 0,
            longTurns45 = lt45,
            longTurns90 = lt90,
            longTurns135 = lt135,
            longTurns180 = lt180,
            longTurns225 = lt225,
            zigZags = zigZags,
            isLShape = abs(ratio - 1.5) < 0.1,
            isUShape = abs(ratio - 2.0) < 0.1,
            hasSiding = switchCount > 0,
            hasDeadEnd = false,
            hasMultiLoop = switchCount >= 2
        )
    }
}
