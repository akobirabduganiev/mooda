package tech.nuqta.mooda.api.controller

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.service.AuthService

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {
    data class RegisterRequest(val country: String, val email: String, val password: String)
    data class RegisterResponse(val sent: Boolean, val verificationToken: String? = null)
    data class LoginRequest(val email: String, val password: String)
    data class RefreshRequest(val refreshToken: String)

    @PostMapping("/register", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun register(@RequestBody body: RegisterRequest): Mono<RegisterResponse> =
        authService.register(body.country, body.email, body.password).map { RegisterResponse(it.sent, it.verificationToken) }

    @PostMapping("/login", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun login(@RequestBody body: LoginRequest): Mono<AuthService.TokenPair> =
        authService.login(body.email, body.password)

    @GetMapping("/verify", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun verify(@RequestParam("token") token: String): Mono<AuthService.VerifyResponse> =
        authService.verifyEmail(token)

    @PostMapping("/refresh", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun refresh(@RequestBody body: RefreshRequest): Mono<AuthService.TokenPair> =
        authService.refresh(body.refreshToken)
}