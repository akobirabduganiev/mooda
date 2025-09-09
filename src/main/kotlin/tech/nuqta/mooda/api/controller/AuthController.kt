package tech.nuqta.mooda.api.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.service.AuthService

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    @param:Value("\${app.google.client-id:}") private val googleClientId: String
) {
    data class GoogleAuthRequest(val idToken: String)
    data class TokenResponse(val accessToken: String, val tokenType: String = "Bearer", val expiresIn: Long)

    @PostMapping("/google", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun googleAuth(@RequestBody body: GoogleAuthRequest): Mono<TokenResponse> {
        return authService.googleAuth(body.idToken, googleClientId.ifBlank { null })
            .map { TokenResponse(accessToken = it.accessToken, expiresIn = it.expiresIn) }
    }
}