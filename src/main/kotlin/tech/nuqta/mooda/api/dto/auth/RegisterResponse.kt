package tech.nuqta.mooda.api.dto.auth

/**
 * Registration response payload
 */
data class RegisterResponse(
    val sent: Boolean,
    val verificationToken: String? = null
)