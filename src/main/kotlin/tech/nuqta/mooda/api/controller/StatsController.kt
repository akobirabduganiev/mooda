package tech.nuqta.mooda.api.controller

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.model.MoodType
import tech.nuqta.mooda.infrastructure.persistence.repository.MoodRepository
import tech.nuqta.mooda.infrastructure.redis.RedisService
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset

@RestController
class StatsController(
    private val redis: RedisService,
    private val moodRepository: MoodRepository
) {
    data class TotalItem(val moodType: String, val count: Long, val percent: Double)
    data class TodayStatsResponse(val totals: List<TotalItem>, val top: List<String>)

    @GetMapping("/api/v1/stats/today", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun today(
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) locale: String?,
        exchange: ServerWebExchange
    ): Mono<TodayStatsResponse> {
        // Set cache headers for edge caches
        exchange.response.headers.add("Cache-Control", "public, max-age=1, stale-while-revalidate=5")

        val today = LocalDate.now(ZoneOffset.UTC)
        // Try Redis first
        return fetchFromRedis(country).flatMap { redisTotals ->
            val sum = redisTotals.values.sum()
            if (sum > 0L) {
                Mono.just(buildResponse(redisTotals))
            } else {
                // Fallback to DB aggregation
                moodRepository.findByDay(today)
                    .filter { entity ->
                        val countryOk = country?.let { c -> entity.country?.equals(c, ignoreCase = true) == true } ?: true
                        val localeOk = locale?.let { l -> entity.locale?.startsWith(l, ignoreCase = true) == true } ?: true
                        countryOk && localeOk
                    }
                    .collectList()
                    .map { list ->
                        val map = MoodType.entries.associateWith { 0L }.toMutableMap()
                        list.forEach { e ->
                            val mt = runCatching { MoodType.valueOf(e.moodType) }.getOrNull()
                            if (mt != null) map[mt] = map.getOrDefault(mt, 0L) + 1
                        }
                        buildResponse(map.mapKeys { it.key.name })
                    }
            }
        }
    }

    private fun fetchFromRedis(country: String?): Mono<Map<String, Long>> {
        val types = MoodType.entries
        val monos = types.map { type ->
            val key = if (country.isNullOrBlank())
                "mooda:cnt:today:mood:${type.name}"
            else
                "mooda:cnt:today:mood:${type.name}:${country.uppercase()}"
            redis.get(key).map { it.toLongOrNull() ?: 0L }.defaultIfEmpty(0L).map { type.name to it }
        }
        return Mono.zip(monos) { arr ->
            arr.map { it as Pair<String, Long> }.toMap()
        }
    }

    private fun buildResponse(countsByType: Map<String, Long>): TodayStatsResponse {
        val total = countsByType.values.sum().coerceAtLeast(0L)
        val totals = MoodType.entries.map { type ->
            val count = countsByType[type.name] ?: 0L
            val percent = if (total > 0) ((count.toDouble() * 100.0) / total.toDouble()) else 0.0
            val rounded = (percent * 10.0).toInt() / 10.0 // one decimal, tolerate rounding
            TotalItem(moodType = type.name, count = count, percent = rounded)
        }
        val top = totals.sortedByDescending { it.count }.take(5).map { it.moodType }
        return TodayStatsResponse(totals = totals, top = top)
    }
}
