package tech.nuqta.mooda.api.dto.mood

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Submit mood request
 */
data class SubmitMoodRequest(
    @field:NotBlank(message = "mood_type_required")
    val moodType: String,

    @field:NotBlank(message = "country_required")
    val country: String,

    @field:Size(max = 500, message = "comment_size")
    val comment: String? = null
)