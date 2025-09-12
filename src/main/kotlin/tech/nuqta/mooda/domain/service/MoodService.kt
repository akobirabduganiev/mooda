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
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*

@Service
class MoodService(
    private val redis: RedisService,
    private val r2dbcProvider: ObjectProvider<R2dbcEntityTemplate>,
    private val countryService: CountryService,
    private val statsService: StatsService,
    @org.springframework.beans.factory.annotation.Value("\${app.timezone:Asia/Tashkent}")
    private val appTimezone: String
) {
    data class SubmitCommand(
        val moodType: String,
        val country: String,
        val comment: String? = null,
        val userId: String?,
        val deviceId: String?,
        val locale: String?,
        val fingerprint: String? = null,
        val deviceCookiePresent: Boolean = false
    )

    data class SubmitResult(val shareCardUrl: String)

    private fun buildStatsJson(dto: StatsService.LiveStatsDto): String {
        val totals = dto.totals.joinToString(prefix = "[", postfix = "]") { t ->
            "{" +
                "\"mood\":\"${t.moodType}\"," +
                "\"count\":${t.count}," +
                "\"percent\":${t.percent}" +
            "}"
        }
        val top = dto.top.joinToString(prefix = "[", postfix = "]") { s -> "\"$s\"" }
        val countryField = dto.country?.let { "\"country\":\"$it\"," } ?: ""
        return "{" +
                "\"scope\":\"${dto.scope}\"," +
                countryField +
                "\"date\":\"${dto.date}\"," +
                "\"totals\":$totals," +
                "\"top\":$top," +
                "\"totalCount\":${dto.totalCount}" +
            "}"
    }

    fun submit(cmd: SubmitCommand): Mono<SubmitResult> {
        val r2dbc = r2dbcProvider.ifAvailable
            ?: return Mono.error(ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "db_unavailable"))
        val moodType = MoodType.from(cmd.moodType)
        val userId = cmd.userId
        val deviceId = cmd.deviceId
        val isUser = !userId.isNullOrBlank()
        val anonSubject = if (cmd.deviceCookiePresent && !deviceId.isNullOrBlank()) deviceId else (cmd.fingerprint ?: deviceId ?: "unknown")
        val subject = userId ?: anonSubject

        if (cmd.country.isBlank()) {
            return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "country_required"))
        }
        val cc = try { countryService.requireValid(cmd.country) } catch (e: ResponseStatusException) { return Mono.error(e) }

        val zone = try { ZoneId.of(appTimezone) } catch (e: Exception) { ZoneOffset.UTC }
        val day = LocalDate.now(zone)
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

                // Daily guard (atomic via Redis SET NX)
                val guardKey = if (isUser) "mooda:submitted:user:$subject:$dayStr" else "mooda:submitted:dev:$subject:$dayStr"
                val nowZ = ZonedDateTime.now(zone)
                val nextDayStart = nowZ.toLocalDate().plusDays(1).atStartOfDay(zone)
                val guardTtl = Duration.between(nowZ, nextDayStart)
                redis.setIfAbsent(guardKey, "1", guardTtl)
                    .onErrorResume { Mono.just(false) }
                    .flatMap { acquired ->
                        if (!acquired) {
                            return@flatMap Mono.error(ResponseStatusException(HttpStatus.CONFLICT, "already_submitted_today"))
                        }

                        val id = UUID.randomUUID().toString()
                        val entity = MoodEntity(
                            id = id,
                            userId = userId,
                            deviceId = deviceId ?: "unknown",
                            moodType = moodType.name,
                            country = cc,
                            locale = cmd.locale,
                            day = day,
                            comment = cmd.comment
                        )
                        return@flatMap r2dbc.insert(entity).flatMap {
                            val counterKeyType = "mooda:cnt:today:mood:${moodType.name}"
                            val counterKeyTypeCountry = "mooda:cnt:today:country:${cc}:${moodType.name}"
                            val countriesSetKey = "mooda:countries:today"
                            val lastKey = if (isUser) "mooda:last:user:$subject" else "mooda:last:dev:$subject"
                            val lastTtl = if (isUser) Duration.ofDays(30) else Duration.ofDays(7)
                            val lastJson = "{\"day\":\"$dayStr\",\"moodType\":\"${moodType.name}\"}"

                            // No need to set guard again; it was already acquired
                            redis.incrementWithTtlIfFirst(counterKeyType, Duration.ofHours(48)).onErrorResume { Mono.just(1L) }.then()
                                .then(redis.incrementWithTtlIfFirst(counterKeyTypeCountry, Duration.ofHours(48)).onErrorResume { Mono.just(1L) }.then())
                                .then(redis.set(lastKey, lastJson, lastTtl).onErrorResume { Mono.just(false) })
                                .then(
                                    // Publish updated stats snapshots to Redis for GLOBAL and COUNTRY scopes
                                    Mono.defer {
                                        val lastTtlStats = Duration.ofHours(48)
                                        val globalMono: Mono<Void> = statsService.live(null, null).flatMap { dto ->
                                            val json = buildStatsJson(dto)
                                            val lastKeyGlobal = "mooda:stats:last:GLOBAL"
                                            redis.set(lastKeyGlobal, json, lastTtlStats).onErrorResume { Mono.just(false) }.then(redis.publish("mooda:stats:GLOBAL", json)).then()
                                        }
                                        val countryMono: Mono<Void> = statsService.live(cc, null).flatMap { dto ->
                                            val json = buildStatsJson(dto)
                                            val lastKeyCountry = "mooda:stats:last:${cc}"
                                            redis.set(lastKeyCountry, json, lastTtlStats).onErrorResume { Mono.just(false) }.then(redis.publish("mooda:stats:${cc}", json)).then()
                                        }
                                        Mono.`when`(globalMono, countryMono)
                                    }
                                )
                                .thenReturn(SubmitResult(shareCardUrl = "/share/$dayStr"))
                        }
                    }
            }
    }
}
