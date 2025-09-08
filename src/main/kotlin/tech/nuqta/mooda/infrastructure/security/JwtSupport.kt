package tech.nuqta.mooda.infrastructure.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class JwtSupport(
    @Value("\${mooda.security.jwt.secret:dev-secret-change}") private val secret: String,
    @Value("\${mooda.security.jwt.expires-min:60}") private val expiresMin: Long
) {
    data class JwtPayload(val userId: String, val provider: String?, val expiresAt: Instant)

    private val keyBytes = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(StandardCharsets.UTF_8))
    private val signer = MACSigner(keyBytes)
    private val verifier = MACVerifier(keyBytes)

    fun generate(userId: String, provider: String = "GOOGLE"): String {
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .subject(userId)
            .issuer("mooda")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(expiresMin * 60)))
            .claim("provider", provider)
            .build()
        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        jwt.sign(signer)
        return jwt.serialize()
    }

    fun expiresInSeconds(): Long = expiresMin * 60

    fun verify(token: String): JwtPayload? {
        return try {
            val jwt = SignedJWT.parse(token)
            if (!jwt.verify(verifier)) return null
            val claims = jwt.jwtClaimsSet
            val exp = claims.expirationTime?.toInstant() ?: return null
            if (exp.isBefore(Instant.now())) return null
            JwtPayload(
                userId = claims.subject,
                provider = claims.getStringClaim("provider"),
                expiresAt = exp
            )
        } catch (e: Exception) {
            null
        }
    }
}