package tech.nuqta.mooda.infrastructure.redis

import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration

interface RedisService {
    fun incrementWithTtlIfFirst(key: String, ttl: Duration): Mono<Long>
    fun get(key: String): Mono<String>
    fun set(key: String, value: String, ttl: Duration): Mono<Boolean>
}

@Component
class RedisServiceImpl(private val redis: ReactiveStringRedisTemplate) : RedisService {
    override fun incrementWithTtlIfFirst(key: String, ttl: Duration): Mono<Long> {
        val ops = redis.opsForValue()
        return ops.increment(key).flatMap { count ->
            if (count == 1L) {
                redis.expire(key, ttl).thenReturn(count)
            } else Mono.just(count)
        }
    }

    override fun get(key: String): Mono<String> {
        return redis.opsForValue().get(key)
    }

    override fun set(key: String, value: String, ttl: Duration): Mono<Boolean> {
        return redis.opsForValue().set(key, value, ttl)
    }
}
