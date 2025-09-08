package tech.nuqta.mooda.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val authenticationManager: ReactiveAuthenticationManager,
    private val converter: BearerTokenServerAuthenticationConverter,
    @Value("\${app.security.cors.allowed-origins:}") private val allowedOriginsProp: String
) {

    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        val authFilter = AuthenticationWebFilter(authenticationManager)
        authFilter.setServerAuthenticationConverter { exchange -> converter.convert(exchange) }
        authFilter.setRequiresAuthenticationMatcher(PathPatternParserServerWebExchangeMatcher("/api/**"))

        // CORS configuration from allowlist property
        val allowedOrigins = allowedOriginsProp.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val corsSource = UrlBasedCorsConfigurationSource()
        if (allowedOrigins.isNotEmpty()) {
            val corsConfig = CorsConfiguration()
            corsConfig.allowedOrigins = allowedOrigins
            corsConfig.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            corsConfig.allowedHeaders = listOf("*")
            corsConfig.allowCredentials = true
            corsConfig.maxAge = 3600
            corsSource.registerCorsConfiguration("/**", corsConfig)
        }

        return http
            .csrf { it.disable() }
            .cors { spec -> if (allowedOrigins.isNotEmpty()) spec.configurationSource(corsSource) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .securityMatcher(PathPatternParserServerWebExchangeMatcher("/**"))
            .authorizeExchange { auth ->
                auth.pathMatchers("/api/v1/auth/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/moods/types").permitAll()
                    .pathMatchers("/api/v1/stats/**").permitAll()
                    .pathMatchers("/actuator/**").permitAll()
                    .pathMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                    .pathMatchers("/api/v1/me/**", "/api/v1/report").authenticated()
                    .anyExchange().permitAll()
            }
            .addFilterAt(authFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build()
    }
}
