package tech.nuqta.mooda.infrastructure.security

import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID

@Component
class DeviceIdWebFilter : WebFilter {
    companion object {
        const val COOKIE_NAME = "mooda_did"
        const val CTX_KEY = "deviceId"
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
                .sameSite("Lax")
                .build()
            exchange.response.addCookie(cookie)
        }
        exchange.attributes[CTX_KEY] = deviceId
        return chain.filter(exchange).contextWrite { ctx -> ctx.put(CTX_KEY, deviceId) }
    }
}