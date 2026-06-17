package com.example.utils

import kotlin.math.min

object FuzzyMatcher {

    /**
     * Compute a similarity score between [source] and [target] from 0.0 to 1.0.
     * 1.0 means exact or very strong match, 0.0 means completely different.
     */
    fun computeMatchScore(source: String, target: String): Double {
        val sClean = cleanString(source)
        val tClean = cleanString(target)

        if (sClean.isEmpty() || tClean.isEmpty()) return 0.0
        if (sClean == tClean) return 1.0

        // 1. Check exact word token containment
        val sTokens = sClean.split(" ").filter { it.length > 1 }
        val tTokens = tClean.split(" ").filter { it.length > 1 }

        if (sTokens.isEmpty() || tTokens.isEmpty()) {
            return computeLevenshteinSimilarity(sClean, tClean)
        }

        // Calculate overlap
        var matchingTokens = 0
        for (sTok in sTokens) {
            // Check if this token matches any token in target closely
            val hasCloseMatch = tTokens.any { sTok == it || computeLevenshteinSimilarity(sTok, it) > 0.85 }
            if (hasCloseMatch) {
                matchingTokens++
            }
        }

        val overlapScore = matchingTokens.toDouble() / sTokens.size.toDouble()
        val levScore = computeLevenshteinSimilarity(sClean, tClean)

        // Combine both scores. Containment overlaps are given more weight for sports matching,
        // because "beIN 1" is a strong match for "beIN Sports HD 1".
        return (overlapScore * 0.70) + (levScore * 0.30)
    }

    private fun cleanString(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s\\u0600-\\u06FF]"), " ") // Keep Arabic, Latin letters and spaces
            .replace(Regex("\\b(hd|sd|fhd|4k|1080p|720p|hevc|h265|h264|premium|vip|ar|en)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun computeLevenshteinSimilarity(str1: String, str2: String): Double {
        val dist = levenshteinDistance(str1, str2)
        val maxLength = maxOf(str1.length, str2.length)
        if (maxLength == 0) return 1.0
        return 1.0 - (dist.toDouble() / maxLength)
    }

    private fun levenshteinDistance(str1: String, str2: String): Int {
        val len1 = str1.length
        val len2 = str2.length
        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)

        for (i in 1..len1) {
            curr[0] = i
            for (j in 1..len2) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,      // Insertion
                    prev[j] + 1,          // Deletion
                    prev[j - 1] + cost    // Substitution
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[len2]
    }
}
