package tech.nuqta.mooda.domain.service

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Abstraction for broadcasting live stats updates to many subscribers per scope (GLOBAL, UZ, US, ...)
 */
interface LiveStatsBroadcaster {
    data class Event(
        val id: String?,
        val scope: String, // e.g. GLOBAL or country code
        val type: String,  // "stats" or "ping"
        val data: String   // JSON string
    )

    fun publish(event: Event)

    fun stream(scope: String): Flux<Event>

    fun activeSubscribers(scope: String? = null): Int
}

@Component
class LiveStatsBroadcasterImpl(
    private val meterRegistry: MeterRegistry
) : LiveStatsBroadcaster {

    private data class Channel(val sink: Sinks.Many<LiveStatsBroadcaster.Event>, val subscribers: AtomicInteger)

    private val channels = ConcurrentHashMap<String, Channel>()

    private val eventsCounter = meterRegistry.counter("mooda.sse.events")

    private fun channel(scope: String): Channel {
        return channels.computeIfAbsent(scope.uppercase()) {
            Channel(
                Sinks.many().multicast().onBackpressureBuffer(1024, false),
                AtomicInteger(0)
            )
        }
    }

    override fun publish(event: LiveStatsBroadcaster.Event) {
        val ch = channel(event.scope)
        ch.sink.tryEmitNext(event)
        eventsCounter.increment()
    }

    override fun stream(scope: String): Flux<LiveStatsBroadcaster.Event> {
        val ch = channel(scope)
        return ch.sink.asFlux()
            .doOnSubscribe { ch.subscribers.incrementAndGet(); meterRegistry.gauge("mooda.sse.subscribers", ch.subscribers) }
            .doFinally { ch.subscribers.decrementAndGet() }
    }

    override fun activeSubscribers(scope: String?): Int {
        return if (scope == null) channels.values.sumOf { it.subscribers.get() } else channel(scope).subscribers.get()
    }
}
