package tech.nuqta.mooda.infrastructure.security

import org.springframework.stereotype.Component

data class GoogleVerificationResult(
    val subject: String,
    val email: String?
)

@Component
class GoogleIdTokenVerifier {
    // DEV stub: accepts idToken == "TEST" and returns a fixed subject/email.
    fun verify(idToken: String, expectedClientId: String? = null): GoogleVerificationResult {
        if (idToken == "TEST") {
            return GoogleVerificationResult(subject = "test-subject", email = "test@example.com")
        }
        // In real impl: verify via Google JWKs and validate aud, iss, exp.
        throw IllegalArgumentException("Invalid Google id_token")
    }
}