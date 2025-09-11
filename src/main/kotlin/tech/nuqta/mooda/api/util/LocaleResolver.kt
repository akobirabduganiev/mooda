package tech.nuqta.mooda.api.util

import java.util.Locale

object LocaleResolver {
    /**
     * Resolves a Locale using optional explicit locale parameter first, then Accept-Language header, then EN.
     * Keeps behavior consistent with existing controllers (supports en, uz, ru fallbacks).
     */
    fun resolve(localeParam: String?, acceptLanguage: String?): Locale {
        if (!localeParam.isNullOrBlank()) return Locale.forLanguageTag(localeParam)
        if (!acceptLanguage.isNullOrBlank()) {
            return try {
                val ranges = Locale.LanguageRange.parse(acceptLanguage)
                Locale.lookup(ranges, listOf(Locale.ENGLISH, Locale("uz"), Locale("ru"))) ?: Locale.ENGLISH
            } catch (_: Exception) {
                Locale.ENGLISH
            }
        }
        return Locale.ENGLISH
    }
}