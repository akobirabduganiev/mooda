package tech.nuqta.mooda.infrastructure.security

import org.springframework.stereotype.Component

// Problem-specific exceptions for Google auth
class InvalidGoogleTokenException(message: String = "Invalid Google id_token") : RuntimeException(message)
class OidcAudienceMismatchException(message: String = "OIDC audience mismatch") : RuntimeException(message)

data class GoogleVerificationResult(
    val subject: String,
    val email: String?
)

@Component
class GoogleIdTokenVerifier {
    // DEV stub verifying Google One Tap id_token.
    // Behavior:
    // - idToken == "TEST" -> success with fixed subject/email
    // - idToken == "TEST_AUD_MISMATCH" -> throw OidcAudienceMismatchException
    // - any other -> throw InvalidGoogleTokenException
    fun verify(idToken: String, expectedClientId: String? = null): GoogleVerificationResult {
        return when (idToken) {
            "TEST" -> GoogleVerificationResult(subject = "test-subject", email = "test@example.com")
            "TEST_AUD_MISMATCH" -> throw OidcAudienceMismatchException()
            else -> throw InvalidGoogleTokenException()
        }
        // Real implementation would verify iss (accounts.google.com or https://accounts.google.com),
        // aud equals expectedClientId, and exp/iat validity using Google's JWKs.
    }
}