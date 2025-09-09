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
    data class RequestSignupRequest(val email: String)
    data class RequestSignupResponse(val sent: Boolean, val verificationToken: String? = null)
    data class RefreshRequest(val refreshToken: String)

    @PostMapping("/request-signup", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun requestSignup(@RequestBody body: RequestSignupRequest): Mono<RequestSignupResponse> =
        authService.requestSignup(body.email).map { RequestSignupResponse(it.sent, it.verificationToken) }

    @GetMapping("/verify", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun verify(@RequestParam("token") token: String): Mono<AuthService.TokenPair> =
        authService.verifyEmail(token)

    @PostMapping("/refresh", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun refresh(@RequestBody body: RefreshRequest): Mono<AuthService.TokenPair> =
        authService.refresh(body.refreshToken)
}