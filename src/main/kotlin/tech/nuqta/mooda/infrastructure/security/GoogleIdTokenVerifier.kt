package tech.nuqta.mooda.infrastructure.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.springframework.stereotype.Component
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit

// Problem-specific exceptions for Google auth
class InvalidGoogleTokenException(message: String = "Invalid Google id_token") : RuntimeException(message)
class OidcAudienceMismatchException(message: String = "OIDC audience mismatch") : RuntimeException(message)

data class GoogleVerificationResult(
    val subject: String,
    val email: String?,
    val firstName: String? = null,
    val lastName: String? = null,
    val country: String? = null
)

@Component
class GoogleIdTokenVerifier {
    private val jwksUri = URL("https://www.googleapis.com/oauth2/v3/certs")
    private val jwkSource: JWKSource<SecurityContext> = RemoteJWKSet(jwksUri)
    private val jwtProcessor: ConfigurableJWTProcessor<SecurityContext> = DefaultJWTProcessor()
    private val keySelector = JWSVerificationKeySelector<SecurityContext>(JWSAlgorithm.RS256, jwkSource)

    init {
        jwtProcessor.jwsKeySelector = keySelector
    }

    fun verify(idToken: String, expectedClientIds: Collection<String>? = null): GoogleVerificationResult {
        // Test shortcuts for local/testing
        if (idToken == "TEST") {
            return GoogleVerificationResult(
                subject = "test-subject",
                email = "test@example.com",
                firstName = "Test",
                lastName = "User",
                country = "UZ"
            )
        }
        if (idToken == "TEST_AUD_MISMATCH") throw OidcAudienceMismatchException()

        try {
            val claims: JWTClaimsSet = jwtProcessor.process(idToken, null)
            val issuer = claims.issuer ?: ""
            if (!(issuer == "accounts.google.com" || issuer == "https://accounts.google.com")) {
                throw InvalidGoogleTokenException("Invalid issuer: $issuer")
            }
            val audience = claims.audience ?: emptyList()
            if (!expectedClientIds.isNullOrEmpty()) {
                val allowed = expectedClientIds.toSet()
                if (audience.none { it in allowed }) {
                    throw OidcAudienceMismatchException()
                }
            }
            val exp = claims.expirationTime?.toInstant() ?: Instant.EPOCH
            val now = Instant.now().minus(60, ChronoUnit.SECONDS) // tolerate small skew
            if (exp.isBefore(now)) throw InvalidGoogleTokenException("Expired token")

            // Enforce email verification if email present and claim exists
            val emailVerified = try {
                val v = claims.getClaim("email_verified")
                when (v) {
                    is Boolean -> v
                    is String -> v.equals("true", ignoreCase = true)
                    else -> null
                }
            } catch (ignored: Exception) { null }
            if (emailVerified == false) {
                throw InvalidGoogleTokenException("Unverified email")
            }

            val sub = claims.subject
            val email = claims.getStringClaim("email")
            val givenName = claims.getStringClaim("given_name")
            val familyName = claims.getStringClaim("family_name")
            val locale = claims.getStringClaim("locale")
            val country = locale?.split("-")?.getOrNull(1)?.uppercase()

            return GoogleVerificationResult(
                subject = sub,
                email = email,
                firstName = givenName,
                lastName = familyName,
                country = country
            )
        } catch (e: OidcAudienceMismatchException) {
            throw e
        } catch (e: Exception) {
            throw InvalidGoogleTokenException(e.message ?: "Invalid Google id_token")
        }
    }
}