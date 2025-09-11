package tech.nuqta.mooda.domain.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.Locale

@Service
class CountryService {
    private val iso2: Set<String> = Locale.getISOCountries().map { it.uppercase(Locale.ROOT) }.toSet()
    private val nameToIso2En: Map<String, String> = iso2.associateBy { code ->
        try { Locale("", code).getDisplayCountry(Locale.ENGLISH).lowercase(Locale.ROOT) } catch (e: Exception) { code.lowercase(Locale.ROOT) }
    }
    private val iso3ToIso2: Map<String, String> = buildIso3ToIso2()

    fun isValid(code: String?): Boolean = normalizeOrNull(code) != null

    fun requireValid(input: String): String = normalizeOrNull(input)
        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "country_invalid")

    /**
     * Strictly require ISO 3166-1 alpha-2 code. Reject names, emojis, ISO3.
     */
    fun requireIso2(input: String): String {
        val up = input.trim().uppercase(Locale.ROOT)
        if (up.length == 2 && iso2.contains(up)) return up
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "country_invalid")
    }

    fun normalizeOrNull(input: String?): String? {
        if (input == null) return null
        val raw = input.trim()
        if (raw.isEmpty()) return null

        // 1) Try flag emoji -> ISO2
        decodeFlagEmoji(raw)?.let { if (iso2.contains(it)) return it }

        val up = raw.uppercase(Locale.ROOT)
        // 2) ISO2
        if (up.length == 2 && iso2.contains(up)) return up
        // 3) ISO3
        if (up.length == 3) iso3ToIso2[up]?.let { return it }
        // 4) English name
        val nameKey = raw.lowercase(Locale.ROOT)
        nameToIso2En[nameKey]?.let { return it }
        return null
    }

    fun listCountries(locale: Locale = Locale.ENGLISH): List<CountryInfo> {
        return iso2
            .sorted()
            .map { code ->
                val loc = Locale("", code)
                val name = try { loc.getDisplayCountry(locale) } catch (e: Exception) { code }
                CountryInfo(code = code, name = name, emoji = toFlagEmoji(code))
            }
    }

    data class CountryInfo(val code: String, val name: String, val emoji: String)

    private fun buildIso3ToIso2(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (code in iso2) {
            try {
                val iso3 = Locale("", code).getISO3Country().uppercase(Locale.ROOT)
                if (iso3.length == 3) map[iso3] = code
            } catch (_: Exception) {
                // ignore
            }
        }
        return map
    }

    private fun decodeFlagEmoji(s: String): String? {
        // Expect two regional indicator symbols
        if (s.codePointCount(0, s.length) != 2) return null
        val it = s.codePoints().iterator()
        val a = if (it.hasNext()) it.nextInt() else return null
        val b = if (it.hasNext()) it.nextInt() else return null
        val base = 0x1F1E6 // 'A'
        fun toChar(cp: Int): Char? {
            val offset = cp - base
            if (offset < 0 || offset > 25) return null
            return ('A'.code + offset).toChar()
        }
        val c1 = toChar(a) ?: return null
        val c2 = toChar(b) ?: return null
        return "" + c1 + c2
    }

    private fun toFlagEmoji(code: String): String {
        val up = code.uppercase(Locale.ROOT)
        if (up.length != 2) return ""
        val base = 0x1F1E6
        val c1 = Character.toChars(base + (up[0].code - 'A'.code))
        val c2 = Character.toChars(base + (up[1].code - 'A'.code))
        return String(c1) + String(c2)
    }
}
