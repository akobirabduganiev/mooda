package tech.nuqta.mooda.api.controller

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.service.StatsService
import tech.nuqta.mooda.api.dto.stats.TodayStatsResponse
import tech.nuqta.mooda.api.dto.stats.TotalItem

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
