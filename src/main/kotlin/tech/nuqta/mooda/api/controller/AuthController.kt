package tech.nuqta.mooda.api.controller

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.security.GoogleIdTokenVerifier
import tech.nuqta.mooda.infrastructure.security.JwtSupport
import java.security.Principal

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val googleVerifier: GoogleIdTokenVerifier,
    private val jwtSupport: JwtSupport
) {
    data class GoogleAuthRequest(val idToken: String)
    data class TokenResponse(val accessToken: String, val tokenType: String = "Bearer", val expiresIn: Long)

    @PostMapping("/google", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun googleAuth(@RequestBody body: GoogleAuthRequest, principal: Principal?): Mono<TokenResponse> {
        return Mono.fromCallable {
            val result = googleVerifier.verify(body.idToken, null)
            // In MVP dev mode, use subject as userId; in real impl, map to DB user
            val userId = "u-${result.subject}"
            val token = jwtSupport.generate(userId, provider = "GOOGLE")
            TokenResponse(accessToken = token, expiresIn = jwtSupport.expiresInSeconds())
        }
    }
}