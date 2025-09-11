package tech.nuqta.mooda.api.controller

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.service.AuthService
import jakarta.validation.Valid
import tech.nuqta.mooda.api.dto.auth.RegisterRequest
import tech.nuqta.mooda.api.dto.auth.RegisterResponse
import tech.nuqta.mooda.api.dto.auth.LoginRequest
import tech.nuqta.mooda.api.dto.auth.RefreshRequest

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun register(@Valid @RequestBody body: RegisterRequest): Mono<RegisterResponse> =
        authService.register(body.country, body.email, body.password).map { RegisterResponse(it.sent, it.verificationToken) }

    @PostMapping("/login", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun login(@Valid @RequestBody body: LoginRequest): Mono<AuthService.TokenPair> =
        authService.login(body.email, body.password)

    @GetMapping("/verify", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun verify(@RequestParam("token") token: String): Mono<AuthService.VerifyResponse> =
        authService.verifyEmail(token)

    @PostMapping("/refresh", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun refresh(@Valid @RequestBody body: RefreshRequest): Mono<AuthService.TokenPair> =
        authService.refresh(body.refreshToken)
}