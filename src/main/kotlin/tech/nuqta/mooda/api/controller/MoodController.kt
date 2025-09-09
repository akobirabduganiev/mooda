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

@RestController
class MoodController(
    private val moodService: MoodService
) {
    data class SubmitMoodRequest(val moodType: String, val country: String, val comment: String? = null)
    data class SubmitMoodResponse(val status: String = "ok", val shareCardUrl: String)

    @PostMapping("/api/v1/mood", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun submitMood(
        @RequestBody body: SubmitMoodRequest,
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
