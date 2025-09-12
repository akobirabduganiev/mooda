package tech.nuqta.mooda.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.reactivestreams.Publisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.context.annotation.Import
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.persistence.entity.MoodEntity
import tech.nuqta.mooda.infrastructure.persistence.repository.MoodRepository
import tech.nuqta.mooda.infrastructure.redis.RedisService
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
    "spring.data.r2dbc.repositories.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration"
])
@AutoConfigureWebTestClient
@Disabled
class MoodApiTests {

    @Autowired
    lateinit var client: WebTestClient

    @Autowired
    lateinit var redis: RedisService

    private val today: LocalDate = LocalDate.now(ZoneId.of("Asia/Tashkent"))

    private fun authToken(): String {
        val tokenMap = client.post()
            .uri("/api/v1/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("idToken" to "TEST"))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody as Map<*, *>
        return tokenMap["accessToken"] as String
    }

    @Test
    fun `submit mood happy path auth`() {
        val token = authToken()

        val resp = client.post()
            .uri("/api/v1/mood")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("moodType" to "HAPPY"))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody as Map<*, *>

        assertThat(resp["status"]).isEqualTo("ok")
        assertThat(resp["shareCardUrl"].toString()).contains(today.toString())

        // Verify last-mood and counter keys
        val last = redis.get("mooda:last:user:u-test-subject").block()
        assertThat(last).isNotNull
        val cnt = redis.get("mooda:cnt:today:mood:HAPPY").block()
        assertThat(cnt).isNotBlank
    }

    @Test
    fun `submit mood happy path anonymous`() {
        val result = client.post()
            .uri("/api/v1/mood")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("moodType" to "CALM"))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()

        val deviceId = result.responseCookies.getFirst("mooda_did")?.value
        assertThat(deviceId).isNotNull

        val resp = result.responseBody as Map<*, *>
        assertThat(resp["status"]).isEqualTo("ok")
        assertThat(resp["shareCardUrl"].toString()).contains(today.toString())

        val last = redis.get("mooda:last:dev:$deviceId").block()
        assertThat(last).isNotNull
        val cnt = redis.get("mooda:cnt:today:mood:CALM").block()
        assertThat(cnt).isNotBlank
    }

    @Test
    fun `invalid mood enum returns 422`() {
        client.post()
            .uri("/api/v1/mood")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("moodType" to "BAD_CODE"))
            .exchange()
            .expectStatus().isEqualTo(422)
            .expectBody()
            .jsonPath("$.code").isEqualTo("invalid_mood_type")
    }

    @Test
    fun `double submit returns 409`() {
        // Simulate guard key pre-set by first submission
        val deviceId = UUID.randomUUID().toString()
        val guardKey = "mooda:submitted:dev:$deviceId:${today}"
        redis.set(guardKey, "1", Duration.ofHours(24)).block()

        client.post()
            .uri("/api/v1/mood")
            .header("Cookie", "mooda_did=$deviceId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("moodType" to "HAPPY"))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.code").isEqualTo("already_submitted_today")
    }

    @Test
    fun `rate limit returns 429`() {
        val deviceId = UUID.randomUUID().toString()
        val rlKey = "mooda:rl:submit:$deviceId"
        // Pre-fill RL bucket to threshold so next increment triggers 429
        redis.set(rlKey, "5", Duration.ofSeconds(60)).block()

        client.post()
            .uri("/api/v1/mood")
            .header("Cookie", "mooda_did=$deviceId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("moodType" to "HAPPY"))
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectBody()
            .jsonPath("$.code").isEqualTo("rate_limited")
    }

    @TestConfiguration
    class TestBeans {
        @Bean
        @Primary
        fun redisService(): RedisService = InMemoryRedis()

        @Bean
        @Primary
        fun moodRepository(): MoodRepository = FakeMoodRepository()
    }
}

class InMemoryRedis : RedisService {
    private data class Entry(var value: String, var expireAt: Long)
    private val store = ConcurrentHashMap<String, Entry>()

    override fun incrementWithTtlIfFirst(key: String, ttl: Duration): Mono<Long> {
        val now = System.currentTimeMillis()
        val e = store.compute(key) { _, old ->
            if (old == null || old.expireAt < now) {
                Entry("1", now + ttl.toMillis())
            } else {
                val next = (old.value.toLongOrNull() ?: 0L) + 1
                old.value = next.toString()
                old
            }
        }!!
        return Mono.just(e.value.toLong())
    }

    override fun get(key: String): Mono<String> {
        val now = System.currentTimeMillis()
        val e = store[key]
        return if (e != null && e.expireAt >= now) Mono.just(e.value) else Mono.empty()
    }

    override fun set(key: String, value: String, ttl: Duration): Mono<Boolean> {
        val expireAt = System.currentTimeMillis() + ttl.toMillis()
        store[key] = Entry(value, expireAt)
        return Mono.just(true)
    }

    override fun setIfAbsent(key: String, value: String, ttl: Duration): Mono<Boolean> {
        val now = System.currentTimeMillis()
        val created = store.compute(key) { _, old ->
            if (old == null || old.expireAt < now) {
                Entry(value, now + ttl.toMillis())
            } else {
                old
            }
        }
        val ok = created != null && (created.value == value && created.expireAt >= now)
        // If key existed and not expired, ok will still be true due to same value; ensure we return false in that case
        val existedAndValid = store[key]?.let { it.expireAt >= now } == true && (store[key]?.value != value || true)
        return Mono.just(!existedAndValid)
    }

    override fun scan(pattern: String): Flux<String> {
        val now = System.currentTimeMillis()
        val regex = Regex("^" + pattern.replace("*", ".*") + "$")
        val keys = store.entries
            .filter { it.value.expireAt >= now }
            .map { it.key }
            .filter { regex.matches(it) }
        return Flux.fromIterable(keys)
    }

    override fun publish(channel: String, message: String): Mono<Long> {
        return Mono.just(0L)
    }

    override fun subscribe(channel: String): Flux<String> {
        return Flux.never()
    }

    override fun subscribePattern(pattern: String): Flux<Pair<String, String>> {
        return Flux.never()
    }
}

class FakeMoodRepository : MoodRepository {
    private val store = mutableMapOf<String, MoodEntity>()

    override fun <S : MoodEntity> save(entity: S): Mono<S> {
        store[entity.id] = entity
        return Mono.just(entity)
    }

    override fun <S : MoodEntity> saveAll(entities: Iterable<S>): Flux<S> = Flux.fromIterable(entities).doOnNext { store[it.id] = it }
    override fun <S : MoodEntity> saveAll(entityStream: Publisher<S>): Flux<S> = Flux.from(entityStream).doOnNext { store[it.id] = it }

    override fun findById(id: String): Mono<MoodEntity> = Mono.justOrEmpty(store[id])
    override fun findById(id: Publisher<String>): Mono<MoodEntity> = Mono.from(id).flatMap { findById(it) }

    override fun existsById(id: String): Mono<Boolean> = Mono.just(store.containsKey(id))
    override fun existsById(id: Publisher<String>): Mono<Boolean> = Mono.from(id).flatMap { existsById(it) }

    override fun findAll(): Flux<MoodEntity> = Flux.fromIterable(store.values)
    override fun findAllById(ids: Iterable<String>): Flux<MoodEntity> = Flux.fromIterable(ids).flatMap { findById(it) }
    override fun findAllById(idStream: Publisher<String>): Flux<MoodEntity> = Flux.from(idStream).flatMap { findById(it) }

    override fun count(): Mono<Long> = Mono.just(store.size.toLong())

    override fun deleteById(id: String): Mono<Void> { store.remove(id); return Mono.empty() }
    override fun deleteById(id: Publisher<String>): Mono<Void> = Mono.from(id).doOnNext { store.remove(it) }.then()

    override fun delete(entity: MoodEntity): Mono<Void> { store.remove(entity.id); return Mono.empty() }
    override fun deleteAllById(ids: Iterable<String>): Mono<Void> { ids.forEach { store.remove(it) }; return Mono.empty() }
    override fun deleteAll(entities: Iterable<MoodEntity>): Mono<Void> { entities.forEach { store.remove(it.id) }; return Mono.empty() }
    override fun deleteAll(entityStream: Publisher<out MoodEntity>): Mono<Void> = Flux.from(entityStream).doOnNext { store.remove(it.id) }.then()
    override fun deleteAll(): Mono<Void> { store.clear(); return Mono.empty() }

    override fun findByUserIdAndDay(userId: String, day: LocalDate): Mono<MoodEntity> = Flux.fromIterable(store.values)
        .filter { it.userId == userId && it.day == day }.next()
    override fun findByDeviceIdAndDay(deviceId: String, day: LocalDate): Mono<MoodEntity> = Flux.fromIterable(store.values)
        .filter { it.deviceId == deviceId && it.day == day }.next()
    override fun findByUserIdOrderByDayDesc(userId: String): Flux<MoodEntity> = Flux.fromIterable(store.values)
        .filter { it.userId == userId }.sort(compareByDescending { it.day })
    override fun findByDay(day: LocalDate): Flux<MoodEntity> = Flux.fromIterable(store.values).filter { it.day == day }
    override fun findByDayBetween(start: LocalDate, end: LocalDate): Flux<MoodEntity> = Flux.fromIterable(store.values)
        .filter { !it.day.isBefore(start) && !it.day.isAfter(end) }
}