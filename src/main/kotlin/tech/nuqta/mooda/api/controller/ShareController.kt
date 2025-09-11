package tech.nuqta.mooda.api.controller

import org.springframework.context.MessageSource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tech.nuqta.mooda.api.dto.stats.ShareTodayResponse
import tech.nuqta.mooda.domain.service.StatsService
import java.util.Locale
import tech.nuqta.mooda.api.util.LocaleResolver

@RestController
class ShareController(
    private val statsService: StatsService,
    private val messageSource: MessageSource,
    private val countryService: tech.nuqta.mooda.domain.service.CountryService
) {

    @GetMapping("/api/v1/share/today", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun todayShare(
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) locale: String?,
        @RequestHeader(name = "Accept-Language", required = false) acceptLanguage: String?,
        exchange: ServerWebExchange
    ): Mono<ShareTodayResponse> {
        // Cache headers for a short time; front can revalidate
        exchange.response.headers.add("Cache-Control", "public, max-age=30, stale-while-revalidate=60")

        val loc = LocaleResolver.resolve(locale, acceptLanguage)
        val cc = country?.let { countryService.requireValid(it) }
        return statsService.live(cc, loc.language)
            .map { live ->
                val countryInfo = live.country?.let { code ->
                    val locObj = Locale("", code)
                    val name = try { locObj.getDisplayCountry(loc) } catch (e: Exception) { code }
                    val emoji = try { countryService.listCountries(loc).firstOrNull { it.code == code }?.emoji } catch (e: Exception) { null }
                    name to emoji
                }
                ShareTodayResponse.fromLive(
                    live = live,
                    countryName = countryInfo?.first,
                    countryEmoji = countryInfo?.second,
                    labelProvider = { code ->
                        try { messageSource.getMessage("mood.type.$code", null, loc) } catch (e: Exception) { code }
                    }
                )
            }
    }
}
