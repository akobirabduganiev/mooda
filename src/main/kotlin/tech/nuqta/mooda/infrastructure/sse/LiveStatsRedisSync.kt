package tech.nuqta.mooda.infrastructure.sse

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.Disposable
import tech.nuqta.mooda.domain.service.LiveStatsBroadcaster
import tech.nuqta.mooda.infrastructure.redis.RedisService

@Component
class LiveStatsRedisSync(
    private val redis: RedisService,
    private val broadcaster: LiveStatsBroadcaster
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var subscription: Disposable? = null

    @PostConstruct
    fun start() {
        // Subscribe to all stats channels
        subscription = redis.subscribePattern("mooda:stats:*")
            .subscribe({ (channel, message) ->
                try {
                    val scope = channel.substringAfterLast(":").uppercase()
                    broadcaster.publish(LiveStatsBroadcaster.Event(
                        id = null,
                        scope = scope,
                        type = "stats",
                        data = message
                    ))
                } catch (e: Exception) {
                    log.warn("Failed to process stats message from {}: {}", channel, e.message)
                }
            }, { err ->
                log.error("LiveStatsRedisSync subscription error", err)
            })
    }
}
