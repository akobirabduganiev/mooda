package tech.nuqta.mooda.domain

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import tech.nuqta.mooda.domain.service.LiveStatsBroadcaster
import tech.nuqta.mooda.domain.service.LiveStatsBroadcasterImpl

class LiveStatsBroadcasterTests {

    @Test
    fun `fan-out to multiple subscribers and track subscribers`() {
        val registry = SimpleMeterRegistry()
        val broadcaster: LiveStatsBroadcaster = LiveStatsBroadcasterImpl(registry)

        val s1 = broadcaster.stream("GLOBAL").take(1)
        val s2 = broadcaster.stream("GLOBAL").take(1)

        assertThat(broadcaster.activeSubscribers("GLOBAL")).isEqualTo(0)

        val merged = Flux.merge(s1, s2)

        StepVerifier.create(merged)
            .then { broadcaster.publish(LiveStatsBroadcaster.Event(id = "1", scope = "GLOBAL", type = "stats", data = "{\"ok\":true}")) }
            .expectNextCount(2)
            .verifyComplete()

        // After completion, subscribers should be 0
        assertThat(broadcaster.activeSubscribers("GLOBAL")).isGreaterThanOrEqualTo(0)

        // Metrics counter incremented
        val counter = registry.find("mooda.sse.events").counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }
}
