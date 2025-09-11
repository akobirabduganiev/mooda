package tech.nuqta.mooda.api.controller

import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.service.MoodService
import tech.nuqta.mooda.infrastructure.security.DeviceIdWebFilter
import jakarta.validation.Valid
import tech.nuqta.mooda.api.dto.mood.SubmitMoodRequest
import tech.nuqta.mooda.api.dto.mood.SubmitMoodResponse

@RestController
class MoodController(
    private val moodService: MoodService
) {

    @PostMapping("/api/v1/mood", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun submitMood(
        @Valid @RequestBody body: SubmitMoodRequest,
        auth: Mono<Authentication>,
        exchange: ServerWebExchange
    ): Mono<SubmitMoodResponse> {
        val locale = exchange.request.headers.getFirst("Accept-Language")
        val deviceId = exchange.attributes[DeviceIdWebFilter.CTX_KEY] as? String

        return auth.map { it.name }.defaultIfEmpty("")
            .flatMap { userIdOrEmpty ->
                val userId = userIdOrEmpty.ifBlank { null }
                moodService.submit(
                    MoodService.SubmitCommand(
                        moodType = body.moodType,
                        country = body.country,
                        comment = body.comment,
                        userId = userId,
                        deviceId = deviceId,
                        locale = locale
                    )
                )
            }
            .map { SubmitMoodResponse(shareCardUrl = it.shareCardUrl) }
    }
}
