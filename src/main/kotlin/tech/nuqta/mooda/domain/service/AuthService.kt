package tech.nuqta.mooda.domain.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.persistence.entity.UserEntity
import tech.nuqta.mooda.infrastructure.persistence.repository.UserRepository
import tech.nuqta.mooda.infrastructure.security.JwtSupport
import tech.nuqta.mooda.infrastructure.service.MailService
import tech.nuqta.mooda.infrastructure.service.EmailTemplates
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

@Service
class AuthService(
    private val userRepo: UserRepository?,
    private val jwtSupport: JwtSupport,
    private val mailService: MailService,
    private val countryService: CountryService,
    @param:Autowired(required = false)
    private val r2dbc: org.springframework.data.r2dbc.core.R2dbcEntityTemplate?,
    @param:Value("\${app.auth.verify-url-base:http://localhost:8010/api/v1/auth/verify}") private val verifyUrlBase: String,
    @param:Value("\${app.auth.debug-expose-token:false}") private val debugExposeToken: Boolean
) {
    data class RequestSignupResponse(val sent: Boolean, val verificationToken: String? = null)
    data class VerifyResponse(val status: String)
    data class TokenPair(val accessToken: String, val refreshToken: String, val expiresIn: Long)

    private val passwordEncoder = BCryptPasswordEncoder()

    fun register(country: String, email: String, password: String): Mono<RequestSignupResponse> {
        val normalized = email.trim().lowercase()
        if (!normalized.contains("@") || normalized.startsWith("@") || normalized.endsWith("@")) {
            throw IllegalArgumentException("Invalid email")
        }
        if (password.length < 6) throw IllegalArgumentException("Password too short")
        val repo = userRepo ?: return Mono.error(ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "user_repo_unavailable"))
        return repo.findByEmail(normalized)
            .flatMap<UserEntity> { Mono.error(ResponseStatusException(HttpStatus.CONFLICT, "email_already_exists")) }
            .switchIfEmpty(Mono.defer {
                val userId = "u-" + UUID.randomUUID().toString()
                val hash = passwordEncoder.encode(password)
                val cc = countryService.requireIso2(country)
                val entity = UserEntity(id = userId, email = normalized, country = cc, passwordHash = hash, emailVerified = false)
                val saveMono: Mono<UserEntity> = r2dbc?.insert(UserEntity::class.java)?.using(entity) ?: repo.save(entity)
                saveMono
            })
            .then(Mono.defer {
                val token = jwtSupport.generateVerification(normalized)
                val link = buildVerifyLink(token)
                val subject = "Verify your email"
                val text = "Please confirm your email by clicking the link: $link"
                val html = EmailTemplates.verificationEmailHtml(appName = "Mooda", actionUrl = link, buttonText = "Verify email")
                mailService.sendEmail(
                    to = normalized,
                    subject = subject,
                    textBody = text,
                    htmlBody = html
                ).onErrorResume { _: Throwable -> Mono.empty() }
                    .then(Mono.just(RequestSignupResponse(sent = true, verificationToken = if (debugExposeToken) token else null)))
            })
    }

    private fun buildVerifyLink(token: String): String {
        val encoded = URLEncoder.encode(token, StandardCharsets.UTF_8)
        return if (verifyUrlBase.contains("?")) "$verifyUrlBase&token=$encoded" else "$verifyUrlBase?token=$encoded"
    }

    fun verifyEmail(token: String): Mono<VerifyResponse> {
        val payload = jwtSupport.verify(token)
        if (payload == null || payload.type != "verify") {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_verification_token")
        }
        val email = payload.email ?: payload.subject
        val repo = userRepo ?: return Mono.error(ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "user_repo_unavailable"))
        return repo.findByEmail(email)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "user_not_found")))
            .flatMap { user ->
                if (user.emailVerified) Mono.just(user) else repo.save(user.copy(emailVerified = true))
            }
            .thenReturn(VerifyResponse(status = "verified"))
    }

    fun login(email: String, password: String): Mono<TokenPair> {
        val normalized = email.trim().lowercase()
        val repo = userRepo ?: return Mono.error(ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "user_repo_unavailable"))
        return repo.findByEmail(normalized)
            .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_credentials")))
            .flatMap { user ->
                val ok = user.passwordHash?.let { passwordEncoder.matches(password, it) } ?: false
                if (!ok) Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_credentials"))
                else if (!user.emailVerified) Mono.error(ResponseStatusException(HttpStatus.FORBIDDEN, "email_not_verified"))
                else Mono.just(user)
            }
            .map { user ->
                val access = jwtSupport.generateAccess(user.id)
                val refresh = jwtSupport.generateRefresh(user.id)
                TokenPair(accessToken = access, refreshToken = refresh, expiresIn = jwtSupport.accessExpiresInSeconds())
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
