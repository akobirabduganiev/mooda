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
    @Value("\${mooda.security.jwt.access-min:15}") private val accessMin: Long,
    @Value("\${mooda.security.jwt.refresh-days:180}") private val refreshDays: Long,
    @Value("\${mooda.security.jwt.verify-hours:24}") private val verifyHours: Long
) {
    data class JwtPayload(val subject: String, val type: String, val expiresAt: Instant, val email: String?)

    private val keyBytes = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(StandardCharsets.UTF_8))
    private val signer = MACSigner(keyBytes)
    private val verifier = MACVerifier(keyBytes)

    private fun build(subject: String, type: String, ttlSeconds: Long, extraClaims: Map<String, Any?> = emptyMap()): String {
        val now = Instant.now()
        val builder = JWTClaimsSet.Builder()
            .subject(subject)
            .issuer("mooda")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
            .claim("typ", type)
        extraClaims.forEach { (k, v) -> if (v != null) builder.claim(k, v) }
        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), builder.build())
        jwt.sign(signer)
        return jwt.serialize()
    }

    fun generateAccess(userId: String): String = build(userId, "access", accessMin * 60)
    fun generateRefresh(userId: String): String = build(userId, "refresh", refreshDays * 24 * 60 * 60)
    fun generateVerification(email: String): String = build(email, "verify", verifyHours * 60 * 60, mapOf("email" to email))

    fun accessExpiresInSeconds(): Long = accessMin * 60

    fun verify(token: String): JwtPayload? {
        return try {
            val jwt = SignedJWT.parse(token)
            if (!jwt.verify(verifier)) return null
            val claims = jwt.jwtClaimsSet
            val exp = claims.expirationTime?.toInstant() ?: return null
            if (exp.isBefore(Instant.now())) return null
            val type = claims.getStringClaim("typ") ?: return null
            val email = try { claims.getStringClaim("email") } catch (e: Exception) { null }
            JwtPayload(
                subject = claims.subject,
                type = type,
                expiresAt = exp,
                email = email
            )
        } catch (e: Exception) { null }
    }
}