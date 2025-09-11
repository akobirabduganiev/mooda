package tech.nuqta.mooda.api.dto.mood

/**
 * Submit mood response
 */
data class SubmitMoodResponse(
    val status: String = "ok",
    val shareCardUrl: String
)