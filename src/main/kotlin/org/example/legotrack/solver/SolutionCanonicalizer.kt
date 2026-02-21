package org.example.legotrack.solver

import org.example.legotrack.model.PlacedPiece

class SolutionCanonicalizer(private val mirrorMap: Map<String, String>) {

    fun getCanonicalSequence(sequence: List<String>, isCycle: Boolean = true): List<String> {
        return getCanonicalSequence(sequence, mirrorMap, isCycle)
    }

    fun getPathSequence(path: List<PlacedPiece>): List<String> {
        return path.map { piece ->
            val deadEnds = piece.deadEndExits.joinToString(",")
            "${piece.definition.id}:${piece.chosenExitIndex}:${piece.isDeadEnd}:${deadEnds}"
        }
    }

    companion object {
        fun getCanonicalSequence(sequence: List<String>, mirrorMap: Map<String, String>, isCycle: Boolean = true): List<String> {
            val s = sequence
            val m = s.map { str ->
                val parts = str.split(":")
                val id = parts[0]
                val mirroredId = mirrorMap[id] ?: id
                (listOf(mirroredId) + parts.drop(1)).joinToString(":")
            }
            val r = s.reversed()
            val mr = m.reversed()

            val equivalents = listOf(s, m, r, mr)

            val allRepresentations = if (isCycle) {
                equivalents.flatMap { eq ->
                    (eq.indices).map { i ->
                        eq.drop(i) + eq.take(i)
                    }
                }
            } else {
                equivalents
            }

            return allRepresentations.minWithOrNull(object : Comparator<List<String>> {
                override fun compare(o1: List<String>, o2: List<String>): Int {
                    for (i in o1.indices) {
                        val cmp = o1[i].compareTo(o2[i])
                        if (cmp != 0) return cmp
                    }
                    return 0
                }
            }) ?: s
        }
    }
}
