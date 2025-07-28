package com.goodstadt.john.language.exams.models

data class CallCost(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val estimatedCharacters: Int,
    val ttsCostUSD: Double,
    val gptInputCostUSD: Double,
    val gptOutputCostUSD: Double,
    val totalCostUSD: Double
)

fun calculateCallCost(
    inputTokens: Int,
    outputTokens: Int,
    charsPerToken: Double = 4.0,
    gptInputCostPerM: Double = 0.10,
    gptOutputCostPerM: Double = 0.40,
    ttsCostPerMChars: Double = 16.0
): CallCost {
    val estimatedChars = (outputTokens * charsPerToken).toInt()

    val gptInputCost = inputTokens / 1_000_000.0 * gptInputCostPerM
    val gptOutputCost = outputTokens / 1_000_000.0 * gptOutputCostPerM
    val ttsCost = estimatedChars / 1_000_000.0 * ttsCostPerMChars

    return CallCost(
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        totalTokens = inputTokens + outputTokens,
        estimatedCharacters = estimatedChars,
        ttsCostUSD = ttsCost,
        gptInputCostUSD = gptInputCost,
        gptOutputCostUSD = gptOutputCost,
        totalCostUSD = gptInputCost + gptOutputCost + ttsCost
    )
}
