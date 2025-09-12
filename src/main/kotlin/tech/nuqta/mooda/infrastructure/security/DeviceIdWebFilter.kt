package tech.nuqta.mooda.infrastructure.security

import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

@Component
class DeviceIdWebFilter : WebFilter {
    companion object {
        const val COOKIE_NAME = "mooda_did"
        const val CTX_KEY = "deviceId"
        const val CTX_FP_KEY = "deviceFingerprint"
        const val CTX_COOKIE_PRESENT = "deviceCookiePresent"
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val existing = exchange.request.cookies.getFirst(COOKIE_NAME)?.value
        val deviceId = existing ?: UUID.randomUUID().toString()
        if (existing == null) {
            val cookie = ResponseCookie.from(COOKIE_NAME, deviceId)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofDays(365))
                .sameSite("None")
                .build()
            exchange.response.addCookie(cookie)
        }
        // Compute lightweight fingerprint to guard anonymous double-submit when cookie isn't present/sent
        val ua = exchange.request.headers.getFirst("User-Agent") ?: ""
        val al = exchange.request.headers.getFirst("Accept-Language") ?: ""
        val xff = exchange.request.headers.getFirst("X-Forwarded-For") ?: ""
        val ip = if (xff.isNotBlank()) xff.split(',')[0].trim() else (exchange.request.remoteAddress?.address?.hostAddress ?: "")
        val seed = listOf(ip, ua, al).joinToString("|")
        val fp = sha256(seed)

        exchange.attributes[CTX_KEY] = deviceId
        exchange.attributes[CTX_FP_KEY] = fp
        exchange.attributes[CTX_COOKIE_PRESENT] = (existing != null)
        return chain.filter(exchange).contextWrite { ctx ->
            ctx.put(CTX_KEY, deviceId).put(CTX_FP_KEY, fp).put(CTX_COOKIE_PRESENT, (existing != null))
        }
    }

    private fun sha256(input: String): String {
        if (input.isBlank()) return "unknown"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }
}