package tech.nuqta.mooda.infrastructure.redis

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration

import reactor.core.publisher.Flux

interface RedisService {
    fun incrementWithTtlIfFirst(key: String, ttl: Duration): Mono<Long>
    fun get(key: String): Mono<String>
    fun set(key: String, value: String, ttl: Duration): Mono<Boolean>
    fun scan(pattern: String): Flux<String>
}

@Component
class RedisServiceImpl(@Autowired(required = false) private val redis: ReactiveStringRedisTemplate?) : RedisService {
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
        val r = redis ?: return Mono.just("0")
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
}
