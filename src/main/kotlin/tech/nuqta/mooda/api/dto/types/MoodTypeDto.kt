package tech.nuqta.mooda.api.dto.types

/**
 * Mood type descriptor for API
 */
data class MoodTypeDto(
    val code: String,
    val label: String,
    val emoji: String
)