package tech.nuqta.mooda.api.controller

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.service.CountryService
import tech.nuqta.mooda.domain.service.LiveStatsBroadcaster
import tech.nuqta.mooda.domain.service.StatsService
import tech.nuqta.mooda.infrastructure.redis.RedisService
import tech.nuqta.mooda.infrastructure.security.JwtSupport
import java.time.Duration

@RestController
class SseController(
    private val broadcaster: LiveStatsBroadcaster,
    private val redis: RedisService,
    private val jwtSupport: JwtSupport,
    private val countryService: CountryService,
    private val statsService: StatsService
) {

    @GetMapping("/api/v1/sse/live", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun live(
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) token: String?,
        exchange: ServerWebExchange
    ): Flux<ServerSentEvent<String>> {
        // Token from query or cookie
        val qToken = token
        val cToken = exchange.request.cookies.getFirst("accessToken")?.value
            ?: exchange.request.cookies.getFirst("mooda_access")?.value
        val tok = qToken ?: cToken
        val payload = tok?.let { jwtSupport.verify(it) }
        if (payload == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized")
        }
        // SSE-friendly headers to avoid proxy buffering and caching
        exchange.response.headers.add("Cache-Control", "no-cache, no-transform")
        exchange.response.headers.add("X-Accel-Buffering", "no")
        exchange.response.headers.add("Connection", "keep-alive")

        val cc = country?.let { countryService.requireValid(it) }
        val scope = (cc ?: "GLOBAL").uppercase()

        // Support Last-Event-ID header (best effort): we will send last snapshot first
        val lastIdHeader = exchange.request.headers.getFirst("Last-Event-ID")
        @Suppress("UNUSED_VARIABLE")
        val lastEventId = lastIdHeader

        val lastKey = "mooda:stats:last:$scope"
        val initial: Mono<ServerSentEvent<String>> = redis.get(lastKey)
            .onErrorResume { Mono.empty() }
            .filter { it.isNotBlank() }
            .map { json ->
                ServerSentEvent.builder<String>()
                    .event("stats")
                    .id(System.currentTimeMillis().toString())
                    .data(json)
                    .build()
            }
            .switchIfEmpty(
                // If we have nothing in Redis yet, compute current snapshot from service as a fallback
                statsService.live(cc, null).map { dto ->
                    val json = toJson(dto)
                    ServerSentEvent.builder<String>()
                        .event("stats")
                        .id(System.currentTimeMillis().toString())
                        .data(json)
                        .build()
                }
            )

        val stream = broadcaster.stream(scope).map { ev ->
            val id = ev.id ?: System.currentTimeMillis().toString()
            ServerSentEvent.builder<String>()
                .event(ev.type)
                .id(id)
                .data(ev.data)
                .build()
        }

        val heartbeat = Flux.interval(Duration.ofSeconds(25))
            .map {
                ServerSentEvent.builder<String>()
                    .event("ping")
                    .data("")
                    .build()
            }

        return Flux.concat(initial, Flux.merge(stream, heartbeat))
            .doOnCancel { /* connection closed */ }
    }

    @GetMapping("/api/v1/sse/public", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun public(exchange: ServerWebExchange): Flux<ServerSentEvent<String>> {
        // Soft rate-limit per IP to avoid abusive reconnect loops
        val ip = exchange.request.headers.getFirst("X-Forwarded-For")?.split(',')?.firstOrNull()?.trim()
            ?: exchange.request.remoteAddress?.address?.hostAddress
            ?: "unknown"
        val rlKey = "mooda:rl:sse:public:$ip"
        val rlTtl = Duration.ofSeconds(15)
        val threshold = 6L

        return redis.incrementWithTtlIfFirst(rlKey, rlTtl)
            .onErrorResume { Mono.just(1L) }
            .flatMapMany { count ->
                if (count > threshold) {
                    return@flatMapMany Flux.error(ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited"))
                }

                // SSE-friendly headers
                exchange.response.headers.add("Cache-Control", "no-cache, no-transform")
                exchange.response.headers.add("X-Accel-Buffering", "no")
                exchange.response.headers.add("Connection", "keep-alive")

                val lastKey = "mooda:stats:last:GLOBAL"
                val initial: Mono<ServerSentEvent<String>> = redis.get(lastKey)
                    .onErrorResume { Mono.empty() }
                    .filter { it.isNotBlank() }
                    .map { json ->
                        ServerSentEvent.builder<String>()
                            .event("stats")
                            .id(System.currentTimeMillis().toString())
                            .data(json)
                            .build()
                    }
                    .switchIfEmpty(
                        statsService.live(null, null).map { dto ->
                            val json = toJson(dto)
                            ServerSentEvent.builder<String>()
                                .event("stats")
                                .id(System.currentTimeMillis().toString())
                                .data(json)
                                .build()
                        }
                    )

                val stream = broadcaster.stream("GLOBAL").map { ev ->
                    val id = ev.id ?: System.currentTimeMillis().toString()
                    ServerSentEvent.builder<String>()
                        .event(ev.type)
                        .id(id)
                        .data(ev.data)
                        .build()
                }

                val heartbeat = Flux.interval(Duration.ofSeconds(25)).map {
                    ServerSentEvent.builder<String>().event("ping").data("").build()
                }

                Flux.concat(initial, Flux.merge(stream, heartbeat))
            }
    }

    private fun toJson(dto: StatsService.LiveStatsDto): String {
        // Small manual JSON to avoid extra dependency
        val totals = dto.totals.joinToString(prefix = "[", postfix = "]") { t ->
            "{" +
                "\"mood\":\"${t.moodType}\"," +
                "\"count\":${t.count}," +
                "\"percent\":${t.percent}" +
            "}"
        }
        val top = dto.top.joinToString(prefix = "[", postfix = "]") { s -> "\"$s\"" }
        val countryField = dto.country?.let { "\"country\":\"$it\"," } ?: ""
        return "{" +
                "\"scope\":\"${dto.scope}\"," +
                countryField +
                "\"date\":\"${dto.date}\"," +
                "\"totals\":$totals," +
                "\"top\":$top," +
                "\"totalCount\":${dto.totalCount}" +
            "}"
    }
}
