package tech.nuqta.mooda.api.controller

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tech.nuqta.mooda.api.dto.stats.TodayStatsResponse
import tech.nuqta.mooda.api.dto.stats.TotalItem
import tech.nuqta.mooda.domain.service.StatsService

@RestController
class StatsController(
    private val statsService: StatsService,
    private val countryService: tech.nuqta.mooda.domain.service.CountryService
) {
    // Legacy response for backward compatibility

    @GetMapping("/api/v1/stats/today", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun today(
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) locale: String?,
        exchange: ServerWebExchange
    ): Mono<TodayStatsResponse> {
        exchange.response.headers.add("Cache-Control", "public, max-age=1, stale-while-revalidate=5")
        val cc = country?.let { countryService.requireValid(it) }
        return statsService.today(cc, locale)
            .map { dto ->
                TodayStatsResponse(
                    totals = dto.totals.map { TotalItem(it.moodType, it.count, it.percent) },
                    top = dto.top
                )
            }
    }

    // New LIVE endpoint
    @GetMapping("/api/v1/stats/live", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun live(
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) locale: String?,
        exchange: ServerWebExchange
    ): Mono<StatsService.LiveStatsDto> {
        exchange.response.headers.add("Cache-Control", "public, max-age=1, stale-while-revalidate=5")
        val cc = country?.let { countryService.requireValid(it) }
        return statsService.live(cc, locale)
    }

    // Stats for an arbitrary day
    @GetMapping("/api/v1/stats/day", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun byDay(
        @RequestParam date: String,
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) locale: String?,
        exchange: ServerWebExchange
    ): Mono<StatsService.LiveStatsDto> {
        val parsed = runCatching { java.time.LocalDate.parse(date) }.getOrElse {
            exchange.response.setStatusCode(org.springframework.http.HttpStatus.BAD_REQUEST)
            return Mono.error(IllegalArgumentException("invalid_date"))
        }
        // Cache headers: past days can be cached longer
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        if (parsed.isBefore(today)) {
            exchange.response.headers.add("Cache-Control", "public, max-age=300, stale-while-revalidate=1200")
        } else {
            exchange.response.headers.add("Cache-Control", "public, max-age=1, stale-while-revalidate=5")
        }
        val cc = country?.let { countryService.requireValid(it) }
        return statsService.byDay(parsed, cc, locale)
    }

    // Range stats: last week/month/year
    @GetMapping("/api/v1/stats/range", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun range(
        @RequestParam(required = false, defaultValue = "week") period: String,
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) locale: String?,
        exchange: ServerWebExchange
    ): Mono<StatsService.RangeStatsDto> {
        val p = period.lowercase()
        val allowed = setOf("week","7d","month","30d","year","365d")
        if (!allowed.contains(p)) {
            exchange.response.setStatusCode(org.springframework.http.HttpStatus.BAD_REQUEST)
            return Mono.error(IllegalArgumentException("invalid_period"))
        }
        // Cache headers tuned per period
        val cache = when (p) {
            "year", "365d" -> "public, max-age=300, stale-while-revalidate=1200"
            "month", "30d" -> "public, max-age=60, stale-while-revalidate=300"
            else -> "public, max-age=30, stale-while-revalidate=120"
        }
        exchange.response.headers.add("Cache-Control", cache)
        val cc = country?.let { countryService.requireValid(it) }
        return statsService.range(p, cc, locale)
    }

    // Leaderboard endpoint
    @GetMapping("/api/v1/stats/leaderboard", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun leaderboard(
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(required = false) locale: String?,
        exchange: ServerWebExchange
    ): Mono<StatsService.LeaderboardDto> {
        exchange.response.headers.add("Cache-Control", "public, max-age=2, stale-while-revalidate=8")
        return statsService.leaderboard(limit)
    }
}
