package tech.nuqta.mooda.api.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Registration request payload
 */
data class RegisterRequest(
    @field:NotBlank(message = "country_required")
    val country: String,

    @field:NotBlank(message = "email_required")
    @field:Email(message = "email_invalid")
    val email: String,

    @field:NotBlank(message = "password_required")
    @field:Size(min = 8, max = 100, message = "password_size")
    val password: String
)