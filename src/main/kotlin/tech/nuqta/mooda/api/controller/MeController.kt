package tech.nuqta.mooda.api.controller

import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.service.MeService

@RestController
@RequestMapping("/api/v1/me")
class MeController(private val meService: MeService) {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun me(auth: Mono<Authentication>): Mono<MeService.MeResponse> =
        auth.flatMap { meService.getProfile(it.name) }

    @GetMapping("/moods", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun myMoods(
        auth: Mono<Authentication>,
        @RequestParam(name = "days", required = false, defaultValue = "7") daysParam: Int?
    ): Mono<MeService.MeMoodsResponse> {
        return auth.flatMap { authentication ->
            val userId = authentication.name
            meService.getMoods(userId, daysParam)
        }
    }
}