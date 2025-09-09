package tech.nuqta.mooda.api.controller

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.service.StatsService

@RestController
class StatsController(
    private val statsService: StatsService
) {
    data class TotalItem(val moodType: String, val count: Long, val percent: Double)
    data class TodayStatsResponse(val totals: List<TotalItem>, val top: List<String>)

    @GetMapping("/api/v1/stats/today", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun today(
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) locale: String?,
        exchange: ServerWebExchange
    ): Mono<TodayStatsResponse> {
        exchange.response.headers.add("Cache-Control", "public, max-age=1, stale-while-revalidate=5")
        return statsService.today(country, locale)
            .map { dto ->
                TodayStatsResponse(
                    totals = dto.totals.map { TotalItem(it.moodType, it.count, it.percent) },
                    top = dto.top
                )
            }
    }
}
