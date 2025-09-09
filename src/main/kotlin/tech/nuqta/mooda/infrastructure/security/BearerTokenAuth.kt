package tech.nuqta.mooda.infrastructure.security

import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

class BearerTokenAuthentication(val token: String, private val userId: String) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_USER"))) {
    override fun getCredentials(): Any = token
    override fun getPrincipal(): Any = userId
}

@Component
class JwtReactiveAuthenticationManager(private val jwtSupport: JwtSupport) : ReactiveAuthenticationManager {
    override fun authenticate(authentication: Authentication): Mono<Authentication> {
        val token = authentication.credentials as? String ?: return Mono.empty()
        val payload = jwtSupport.verify(token) ?: return Mono.empty()
        if (payload.type != "access") return Mono.empty()
        val userId = payload.subject
        val auth = BearerTokenAuthentication(token, userId)
        auth.isAuthenticated = true
        return Mono.just(auth)
    }
}

@Component
class BearerTokenServerAuthenticationConverter {
    fun convert(exchange: ServerWebExchange): Mono<Authentication> {
        val header = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION) ?: return Mono.empty()
        if (!header.startsWith("Bearer ")) return Mono.empty()
        val token = header.removePrefix("Bearer ").trim()
        return Mono.just(object : AbstractAuthenticationToken(null) {
            override fun getCredentials(): Any = token
            override fun getPrincipal(): Any? = null
        })
    }
}