package tech.nuqta.mooda.api.controller

import org.springframework.context.MessageSource
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tech.nuqta.mooda.api.dto.types.CountryDto
import tech.nuqta.mooda.api.dto.types.MoodTypeDto
import tech.nuqta.mooda.api.util.LocaleResolver
import tech.nuqta.mooda.domain.model.MoodType

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
        val resolved = LocaleResolver.resolve(locale, acceptLanguage)
        return MoodType.entries.map {
            val labelKey = "mood.type.${it.name}"
            val label = try {
                messageSource.getMessage(labelKey, null, resolved)
            } catch (_: Exception) {
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
        val resolved = LocaleResolver.resolve(locale, acceptLanguage)
        return countryService.listCountries(resolved).map { CountryDto(code = it.code, name = it.name, emoji = it.emoji) }
    }
}
