package tech.nuqta.mooda.api.controller

import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Instant

@RestController
@RequestMapping("/api/v1/me")
class MeController {
    data class MeResponse(
        val userId: String,
        val email: String? = null,
        val providers: List<String> = listOf("GOOGLE"),
        val createdAt: Instant = Instant.EPOCH
    )

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun me(auth: Mono<Authentication>): Mono<MeResponse> =
        auth.map { MeResponse(userId = it.name ?: "unknown") }
}