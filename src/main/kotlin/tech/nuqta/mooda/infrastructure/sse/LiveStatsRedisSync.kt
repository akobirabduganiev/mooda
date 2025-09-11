package tech.nuqta.mooda.infrastructure.sse

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.Disposable
import reactor.util.retry.Retry
import tech.nuqta.mooda.domain.service.LiveStatsBroadcaster
import tech.nuqta.mooda.infrastructure.redis.RedisService
import java.time.Duration

@Component
class LiveStatsRedisSync(
    private val redis: RedisService,
    private val broadcaster: LiveStatsBroadcaster
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var subscription: Disposable? = null

    @PostConstruct
    fun start() {
        // Subscribe to all stats channels with retry/backoff
        subscription = redis.subscribePattern("mooda:stats:*")
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                    .maxBackoff(Duration.ofSeconds(30))
                    .jitter(0.5)
            )
            .subscribe({ (channel, message) ->
                try {
                    val scope = channel.substringAfterLast(":").uppercase()
                    broadcaster.publish(
                        LiveStatsBroadcaster.Event(
                            id = null,
                            scope = scope,
                            type = "stats",
                            data = message
                        )
                    )
                } catch (e: Exception) {
                    log.warn("Failed to process stats message from {}: {}", channel, e.message)
                }
            }, { err ->
                log.error("LiveStatsRedisSync subscription error", err)
            })
    }

    @PreDestroy
    fun stop() {
        try {
            subscription?.dispose()
        } catch (e: Exception) {
            log.debug("Error during LiveStatsRedisSync dispose: {}", e.message)
        }
    }
}
