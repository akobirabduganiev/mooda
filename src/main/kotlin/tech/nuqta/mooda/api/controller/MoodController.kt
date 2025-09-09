package tech.nuqta.mooda.api.controller

import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.model.MoodType
import tech.nuqta.mooda.infrastructure.persistence.entity.MoodEntity
import tech.nuqta.mooda.infrastructure.persistence.repository.MoodRepository
import tech.nuqta.mooda.infrastructure.redis.RedisService
import tech.nuqta.mooda.infrastructure.security.DeviceIdWebFilter
import java.time.*
import org.springframework.http.HttpStatus
import java.util.*
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate

@RestController
class MoodController(
    private val moodRepository: MoodRepository,
    private val redis: RedisService,
    private val r2dbc: R2dbcEntityTemplate
) {
    data class SubmitMoodRequest(val moodType: String)
    data class SubmitMoodResponse(val status: String = "ok", val shareCardUrl: String)

    @PostMapping("/api/v1/mood", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun submitMood(
        @RequestBody body: SubmitMoodRequest,
        auth: Mono<Authentication>,
        exchange: ServerWebExchange
    ): Mono<SubmitMoodResponse> {
        val day = LocalDate.now(ZoneOffset.UTC)
        val dayStr = day.toString()
        val locale = exchange.request.headers.getFirst("Accept-Language")
        val deviceId = exchange.attributes[DeviceIdWebFilter.CTX_KEY] as? String
        val moodType = MoodType.from(body.moodType)

        return auth.map { it.name }.defaultIfEmpty("").flatMap { userIdOrEmpty ->
            val userId = userIdOrEmpty.ifBlank { null }
            val subject = userId ?: deviceId ?: "unknown"
            val isUser = userId != null

            // rate limit
            val rlKey = "mooda:rl:submit:$subject"
            val rlTtl = Duration.ofSeconds(60)
            val rlThreshold = 5L

            redis.incrementWithTtlIfFirst(rlKey, rlTtl)
                .onErrorResume { Mono.just(1L) }
                .flatMap { count ->
                if (count > rlThreshold) {
                    return@flatMap Mono.error(ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited"))
                }

                val guardKey = if (isUser) "mooda:submitted:user:$subject:$dayStr" else "mooda:submitted:dev:$subject:$dayStr"

                redis.get(guardKey)
                    .onErrorResume { Mono.empty() }
                    .defaultIfEmpty("")
                    .flatMap { existing ->
                    if (existing.isNotEmpty()) {
                        return@flatMap Mono.error(ResponseStatusException(HttpStatus.CONFLICT, "already_submitted_today"))
                    }

                    val id = UUID.randomUUID().toString()
                    val entity = MoodEntity(
                        id = id,
                        userId = userId,
                        deviceId = deviceId ?: "unknown",
                        moodType = moodType.name,
                        country = null,
                        locale = locale,
                        day = day
                    )

                    r2dbc.insert(entity).flatMap {
                        // set guard 24h
                        val guardTtl = Duration.ofHours(24)
                        val counterKey = "mooda:cnt:today:mood:${moodType.name}"
                        val lastKey = if (isUser) "mooda:last:user:$subject" else "mooda:last:dev:$subject"
                        val lastTtl = if (isUser) Duration.ofDays(30) else Duration.ofDays(7)
                        val lastJson = "{\"day\":\"$dayStr\",\"moodType\":\"${moodType.name}\"}"

                        redis.set(guardKey, "1", guardTtl).onErrorResume { Mono.just(false) }
                            .then(redis.incrementWithTtlIfFirst(counterKey, Duration.ofHours(48)).onErrorResume { Mono.just(1L) }.then())
                            .then(redis.set(lastKey, lastJson, lastTtl).onErrorResume { Mono.just(false) })
                            .thenReturn(SubmitMoodResponse(shareCardUrl = "/share/$dayStr"))
                    }
                }
            }
        }
    }
}
