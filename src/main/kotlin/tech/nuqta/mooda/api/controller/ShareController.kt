package tech.nuqta.mooda.api.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.service.StatsService
import java.util.Locale

@RestController
class ShareController(
    private val statsService: StatsService,
    private val messageSource: MessageSource,
    private val countryService: tech.nuqta.mooda.domain.service.CountryService,
    @Value("\${app.share.png-enabled:false}") private val pngEnabled: Boolean
) {

    @GetMapping("/api/v1/share/today.{format}")
    fun todayShare(
        @PathVariable format: String,
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) locale: String?,
        @RequestHeader(name = "Accept-Language", required = false) acceptLanguage: String?,
        exchange: ServerWebExchange
    ): Mono<String> {
        // Always set cache headers
        exchange.response.headers.add("Cache-Control", "public, max-age=30, stale-while-revalidate=60")
        // Content type: return SVG for both svg and png (when png not enabled)
        exchange.response.headers.contentType = MediaType.valueOf("image/svg+xml")

        val loc = resolveLocale(locale, acceptLanguage)
        val cc = country?.let { countryService.requireValid(it) }
        return statsService.live(cc, loc.language)
            .map { dto ->
                val title = if (dto.scope == "GLOBAL")
                    msg("share.title.global", loc)
                else
                    msg("share.title.country", loc, arrayOf(dto.country ?: ""))

                renderSvgShareCard(title, dto, loc)
            }
    }

    private fun renderSvgShareCard(title: String, dto: StatsService.LiveStatsDto, locale: Locale): String {
        val width = 1000
        val padding = 24
        val rowHeight = 48
        val headerHeight = 80
        val footerHeight = 40
        val rows = dto.totals.take(5)
        val height = headerHeight + rows.size * (rowHeight + 12) + footerHeight + padding
        val footerText = (msg("share.footer", locale)).ifBlank { "mooda.app" } + " • " + dto.date

        val bars = StringBuilder()
        val maxPercent = (rows.maxOfOrNull { it.percent } ?: 0.0).coerceAtLeast(1.0)
        var y = headerHeight
        rows.forEach { item ->
            val barW = ((width - padding * 2 - 220) * (item.percent / maxPercent)).toInt()
            val yCenter = y + rowHeight - 18
            bars.append(
                """
                <g>
                  <text x="${padding}" y="${yCenter}" font-size="24">${item.emoji}</text>
                  <text x="${padding + 36}" y="${yCenter}" font-size="18" fill="#111">${item.moodType}</text>
                  <rect x="${padding + 200}" y="${yCenter - 18}" width="${barW}" height="24" rx="12" fill="#6EE7B7"/>
                  <text x="${padding + 200 + barW + 8}" y="${yCenter}" font-size="16" fill="#333">${String.format("%.1f", item.percent)}%</text>
                </g>
                """
            )
            y += rowHeight + 12
        }

        return """
            <svg xmlns='http://www.w3.org/2000/svg' width='${width}' height='${height}' viewBox='0 0 ${width} ${height}'>
              <defs>
                <linearGradient id='bg' x1='0' x2='1' y1='0' y2='1'>
                  <stop offset='0%' stop-color='#F0FDFA'/>
                  <stop offset='100%' stop-color='#F9FAFB'/>
                </linearGradient>
              </defs>
              <rect width='100%' height='100%' fill='url(#bg)'/>
              <text x='${padding}' y='48' font-size='28' font-weight='700' fill='#111'>${title}</text>
              <text x='${padding}' y='72' font-size='16' fill='#555'>Total: ${dto.totalCount}</text>
              $bars
              <text x='${padding}' y='${height - padding}' font-size='14' fill='#777'>${footerText}</text>
            </svg>
        """.trimIndent()
    }

    private fun msg(key: String, locale: Locale, args: Array<Any> = emptyArray()): String = try {
        messageSource.getMessage(key, args, locale)
    } catch (e: Exception) {
        ""
    }

    private fun resolveLocale(localeParam: String?, acceptLanguage: String?): Locale {
        if (!localeParam.isNullOrBlank()) return Locale.forLanguageTag(localeParam)
        if (!acceptLanguage.isNullOrBlank()) {
            return try {
                val ranges = Locale.LanguageRange.parse(acceptLanguage)
                Locale.lookup(ranges, listOf(Locale.ENGLISH, Locale("uz"), Locale("ru"))) ?: Locale.ENGLISH
            } catch (e: Exception) { Locale.ENGLISH }
        }
        return Locale.ENGLISH
    }
}
