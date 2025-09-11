package tech.nuqta.mooda.api.controller

import org.springframework.context.MessageSource
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tech.nuqta.mooda.domain.model.MoodType
import java.util.Locale
import tech.nuqta.mooda.api.dto.types.MoodTypeDto
import tech.nuqta.mooda.api.dto.types.CountryDto

@RestController
class TypesController(
    private val messageSource: MessageSource,
    private val countryService: tech.nuqta.mooda.domain.service.CountryService
) {

    @GetMapping("/api/v1/moods/types")
    fun getMoodTypes(
        @RequestParam(required = false) locale: String?,
        @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?
    ): List<MoodTypeDto> {
        val resolved = resolveLocale(locale, acceptLanguage)
        return MoodType.entries.map {
            val labelKey = "mood.type.${it.name}"
            val label = try {
                messageSource.getMessage(labelKey, null, resolved)
            } catch (e: Exception) {
                it.name
            }
            MoodTypeDto(code = it.name, label = label, emoji = it.defaultEmoji)
        }
    }

    @GetMapping("/api/v1/types/countries")
    fun getCountries(
        @RequestParam(required = false) locale: String?,
        @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) acceptLanguage: String?
    ): List<CountryDto> {
        val resolved = resolveLocale(locale, acceptLanguage)
        return countryService.listCountries(resolved).map { CountryDto(code = it.code, name = it.name, emoji = it.emoji) }
    }

    private fun resolveLocale(localeParam: String?, acceptLanguage: String?): Locale {
        if (!localeParam.isNullOrBlank()) {
            return Locale.forLanguageTag(localeParam)
        }
        if (!acceptLanguage.isNullOrBlank()) {
            return try {
                val ranges = Locale.LanguageRange.parse(acceptLanguage)
                Locale.lookup(ranges, listOf(Locale.ENGLISH, Locale("uz"), Locale("ru"))) ?: Locale.ENGLISH
            } catch (e: Exception) {
                Locale.ENGLISH
            }
        }
        return Locale.ENGLISH
    }
}
