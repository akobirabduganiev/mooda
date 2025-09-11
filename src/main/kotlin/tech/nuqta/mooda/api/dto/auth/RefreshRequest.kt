package tech.nuqta.mooda.api.dto.auth

import jakarta.validation.constraints.NotBlank

/**
 * Refresh token request payload
 */
data class RefreshRequest(
    @field:NotBlank(message = "refresh_token_required")
    val refreshToken: String
)