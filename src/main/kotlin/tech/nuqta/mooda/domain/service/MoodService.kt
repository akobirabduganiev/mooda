package tech.nuqta.mooda.domain.service

import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.model.MoodType
import tech.nuqta.mooda.infrastructure.persistence.entity.MoodEntity
import tech.nuqta.mooda.infrastructure.redis.RedisService
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

@Service
class MoodService(
    private val redis: RedisService,
    private val r2dbcProvider: ObjectProvider<R2dbcEntityTemplate>
) {
    data class SubmitCommand(
        val moodType: String,
        val country: String,
        val comment: String? = null,
        val userId: String?,
        val deviceId: String?,
        val locale: String?
    )

    data class SubmitResult(val shareCardUrl: String)

    fun submit(cmd: SubmitCommand): Mono<SubmitResult> {
        val r2dbc = r2dbcProvider.ifAvailable
            ?: return Mono.error(ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "db_unavailable"))
        val moodType = MoodType.from(cmd.moodType)
        val userId = cmd.userId
        val deviceId = cmd.deviceId
        val isUser = !userId.isNullOrBlank()
        val subject = userId ?: deviceId ?: "unknown"

        if (cmd.country.isBlank()) {
            return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "country_required"))
        }

        val day = LocalDate.now(ZoneOffset.UTC)
        val dayStr = day.toString()

        // Rate limiting
        val rlKey = "mooda:rl:submit:$subject"
        val rlTtl = Duration.ofSeconds(60)
        val rlThreshold = 5L

        return redis.incrementWithTtlIfFirst(rlKey, rlTtl)
            .onErrorResume { Mono.just(1L) }
            .flatMap { count ->
                if (count > rlThreshold) {
                    return@flatMap Mono.error(ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited"))
                }

                // Daily guard
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
                            country = cmd.country.uppercase(),
                            locale = cmd.locale,
                            day = day,
                            comment = cmd.comment
                        )
                        r2dbc.insert(entity).flatMap {
                            val guardTtl = Duration.ofHours(24)
                            val counterKeyType = "mooda:cnt:today:mood:${moodType.name}"
                            val counterKeyTypeCountry = "mooda:cnt:today:country:${cmd.country.uppercase()}:${moodType.name}"
                            val countriesSetKey = "mooda:countries:today"
                            val lastKey = if (isUser) "mooda:last:user:$subject" else "mooda:last:dev:$subject"
                            val lastTtl = if (isUser) Duration.ofDays(30) else Duration.ofDays(7)
                            val lastJson = "{\"day\":\"$dayStr\",\"moodType\":\"${moodType.name}\"}"

                            redis.set(guardKey, "1", guardTtl).onErrorResume { Mono.just(false) }
                                .then(redis.incrementWithTtlIfFirst(counterKeyType, Duration.ofHours(48)).onErrorResume { Mono.just(1L) }.then())
                                .then(redis.incrementWithTtlIfFirst(counterKeyTypeCountry, Duration.ofHours(48)).onErrorResume { Mono.just(1L) }.then())
                                .then(redis.set(lastKey, lastJson, lastTtl).onErrorResume { Mono.just(false) })
                                .thenReturn(SubmitResult(shareCardUrl = "/share/$dayStr"))
                        }
                    }
            }
    }
}
