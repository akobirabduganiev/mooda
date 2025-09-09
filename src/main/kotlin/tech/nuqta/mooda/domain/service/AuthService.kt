package tech.nuqta.mooda.domain.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.persistence.entity.UserEntity
import tech.nuqta.mooda.infrastructure.persistence.repository.UserRepository
import tech.nuqta.mooda.infrastructure.security.JwtSupport
import tech.nuqta.mooda.infrastructure.service.MailService
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

@Service
class AuthService(
    private val userRepo: UserRepository?,
    private val jwtSupport: JwtSupport,
    private val mailService: MailService,
    @Value("\${app.auth.verify-url-base:http://localhost:8010/api/v1/auth/verify}") private val verifyUrlBase: String,
    @Value("\${app.auth.debug-expose-token:false}") private val debugExposeToken: Boolean
) {
    data class RequestSignupResponse(val sent: Boolean, val verificationToken: String? = null)
    data class TokenPair(val accessToken: String, val refreshToken: String, val expiresIn: Long)

    fun requestSignup(email: String): Mono<RequestSignupResponse> {
        val normalized = email.trim().lowercase()
        if (!normalized.contains("@") || normalized.startsWith("@") || normalized.endsWith("@")) {
            throw IllegalArgumentException("Invalid email")
        }
        val token = jwtSupport.generateVerification(normalized)
        val link = buildVerifyLink(token)
        return mailService.sendEmail(
            to = normalized,
            subject = "Confirm your email",
            body = "Please confirm your email by clicking the link: $link"
        ).onErrorResume { _: Throwable -> Mono.empty() }
            .then(Mono.just(RequestSignupResponse(sent = true, verificationToken = if (debugExposeToken) token else null)))
    }

    private fun buildVerifyLink(token: String): String {
        val encoded = URLEncoder.encode(token, StandardCharsets.UTF_8)
        return if (verifyUrlBase.contains("?")) "$verifyUrlBase&token=$encoded" else "$verifyUrlBase?token=$encoded"
    }

    fun verifyEmail(token: String): Mono<TokenPair> {
        val payload = jwtSupport.verify(token)
        if (payload == null || payload.type != "verify") {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_verification_token")
        }
        val email = payload.email ?: payload.subject
        val repo = userRepo
        return if (repo == null) {
            val userId = "u-" + UUID.randomUUID().toString()
            val access = jwtSupport.generateAccess(userId)
            val refresh = jwtSupport.generateRefresh(userId)
            Mono.just(TokenPair(accessToken = access, refreshToken = refresh, expiresIn = jwtSupport.accessExpiresInSeconds()))
        } else {
            repo.findByEmail(email)
                .switchIfEmpty(Mono.defer {
                    val userId = "u-" + UUID.randomUUID().toString()
                    repo.save(UserEntity(id = userId, email = email))
                })
                .map { user ->
                    val access = jwtSupport.generateAccess(user.id)
                    val refresh = jwtSupport.generateRefresh(user.id)
                    TokenPair(accessToken = access, refreshToken = refresh, expiresIn = jwtSupport.accessExpiresInSeconds())
                }
        }
    }

    fun refresh(refreshToken: String): Mono<TokenPair> {
        val payload = jwtSupport.verify(refreshToken)
        if (payload == null || payload.type != "refresh") {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token")
        }
        val userId = payload.subject
        val access = jwtSupport.generateAccess(userId)
        // Return same refresh token (no rotation) with configured expiry info
        return Mono.just(TokenPair(accessToken = access, refreshToken = refreshToken, expiresIn = jwtSupport.accessExpiresInSeconds()))
    }
}
