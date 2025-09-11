package tech.nuqta.mooda.infrastructure.redis

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

interface RedisService {
    fun incrementWithTtlIfFirst(key: String, ttl: Duration): Mono<Long>
    fun get(key: String): Mono<String>
    fun set(key: String, value: String, ttl: Duration): Mono<Boolean>
    fun scan(pattern: String): Flux<String>

    // Pub/Sub
    fun publish(channel: String, message: String): Mono<Long>
    fun subscribe(channel: String): Flux<String>
    fun subscribePattern(pattern: String): Flux<Pair<String, String>>
}

@Component
class RedisServiceImpl(
    @Autowired(required = false) private val redis: ReactiveStringRedisTemplate?,
    @Autowired(required = false) private val factory: ReactiveRedisConnectionFactory?
) : RedisService {
    private val listenerContainer: ReactiveRedisMessageListenerContainer? = factory?.let { ReactiveRedisMessageListenerContainer(it) }

    override fun incrementWithTtlIfFirst(key: String, ttl: Duration): Mono<Long> {
        val r = redis ?: return Mono.just(1L)
        val ops = r.opsForValue()
        return ops.increment(key).flatMap { count ->
            if (count == 1L) {
                r.expire(key, ttl).thenReturn(count)
            } else Mono.just(count)
        }
    }

    override fun get(key: String): Mono<String> {
        val r = redis ?: return Mono.empty()
        return r.opsForValue().get(key)
    }

    override fun set(key: String, value: String, ttl: Duration): Mono<Boolean> {
        val r = redis ?: return Mono.just(true)
        return r.opsForValue().set(key, value, ttl)
    }

    override fun scan(pattern: String): Flux<String> {
        val r = redis ?: return Flux.empty()
        val options = org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(1000L).build()
        return r.scan(options)
    }

    override fun publish(channel: String, message: String): Mono<Long> {
        val r = redis ?: return Mono.just(0L)
        return r.convertAndSend(channel, message)
    }

    override fun subscribe(channel: String): Flux<String> {
        val container = listenerContainer ?: return Flux.empty()
        return container.receive(ChannelTopic(channel)).map { msg ->
            decodeToString(msg.message)
        }
    }

    override fun subscribePattern(pattern: String): Flux<Pair<String, String>> {
        val container = listenerContainer ?: return Flux.empty()
        return container.receive(PatternTopic(pattern)).map { msg ->
            decodeToString(msg.channel) to decodeToString(msg.message)
        }
    }

    private fun decodeToString(value: Any?): String {
        return when (value) {
            is String -> value
            is CharSequence -> value.toString()
            is ByteArray -> String(value, StandardCharsets.UTF_8)
            is ByteBuffer -> {
                val buf = value.asReadOnlyBuffer()
                val bytes = ByteArray(buf.remaining())
                buf.get(bytes)
                String(bytes, StandardCharsets.UTF_8)
            }
            else -> value?.toString() ?: ""
        }
    }
}
