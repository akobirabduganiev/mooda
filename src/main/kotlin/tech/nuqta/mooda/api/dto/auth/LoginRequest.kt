package tech.nuqta.mooda.api.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/**
 * Login request payload
 */
data class LoginRequest(
    @field:NotBlank(message = "email_required")
    @field:Email(message = "email_invalid")
    val email: String,

    @field:NotBlank(message = "password_required")
    val password: String
)