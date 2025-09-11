package tech.nuqta.mooda.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.persistence.entity.MoodEntity
import tech.nuqta.mooda.infrastructure.persistence.entity.UserEntity
import tech.nuqta.mooda.infrastructure.persistence.repository.MoodRepository
import tech.nuqta.mooda.infrastructure.persistence.repository.UserRepository
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiTests {

    @Autowired
    lateinit var client: WebTestClient

    private fun registerVerifyLogin(email: String = "test@example.com", password: String = "password", country: String = "UZ"): String {
        val reg = client.post()
            .uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("country" to country, "email" to email, "password" to password))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody as Map<*, *>
        val verificationToken = reg["verificationToken"] as String
        assertThat(verificationToken).isNotBlank

        client.get()
            .uri("/api/v1/auth/verify?token=$verificationToken")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("verified")

        val pair = client.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to email, "password" to password))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody as Map<*, *>
        return pair["accessToken"] as String
    }

    @Test
    fun `moods types returns localized labels`() {
        val body = client.get()
            .uri("/api/v1/moods/types?locale=uz")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Map::class.java)
            .returnResult()
            .responseBody!!

        val happy = body.firstOrNull { it["code"] == "HAPPY" }
        assertThat(happy).isNotNull
        assertThat(happy!!["label"]).isEqualTo("Baxtli")
        assertThat(happy["emoji"]).isNotNull
    }

    @Test
    fun `email register verify login and me works`() {
        val token = registerVerifyLogin()

        client.get()
            .uri("/api/v1/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").value<String> { v -> assertThat(v).startsWith("u-") }
    }

    @Test
    fun `moods types returns RU localized labels`() {
        val body = client.get()
            .uri("/api/v1/moods/types?locale=ru")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Map::class.java)
            .returnResult()
            .responseBody!!

        val happy = body.firstOrNull { it["code"] == "HAPPY" }
        assertThat(happy).isNotNull
        assertThat(happy!!["label"]).isEqualTo("Счастливый")
    }

    @Test
    fun `me moods requires auth`() {
        client.get()
            .uri("/api/v1/me/moods")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `me moods with auth returns empty items`() {
        val token = registerVerifyLogin(email = "user2@example.com")

        client.get()
            .uri("/api/v1/me/moods?days=0")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items").isArray
            .jsonPath("$.items.length()").isEqualTo(0)
    }

    @Test
    fun `types countries returns list with flags`() {
        val body = client.get()
            .uri("/api/v1/types/countries?locale=en")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBodyList(Map::class.java)
            .returnResult()
            .responseBody!!

        assertThat(body.size).isGreaterThan(10)
        val uz = body.firstOrNull { it["code"] == "UZ" } as Map<*, *>?
        assertThat(uz).isNotNull
        assertThat(uz!!["emoji"]).isEqualTo("🇺🇿")
        assertThat(uz["name"]).isNotNull
    }

    @TestConfiguration
    class TestBeans {
        @Bean
        @Primary
        fun moodRepository(): MoodRepository = ApiFakeMoodRepository()

        @Bean
        @Primary
        fun userRepository(): UserRepository = InMemoryUserRepository()
    }
}

class InMemoryUserRepository : UserRepository {
    private val byId = ConcurrentHashMap<String, UserEntity>()

    override fun findByEmail(email: String): Mono<UserEntity> = Mono.justOrEmpty(byId.values.firstOrNull { it.email == email })

    override fun <S : UserEntity> save(entity: S): Mono<S> {
        byId[entity.id] = entity
        return Mono.just(entity)
    }

    override fun <S : UserEntity> saveAll(entities: Iterable<S>): Flux<S> = Flux.fromIterable(entities).doOnNext { byId[it.id] = it }
    override fun <S : UserEntity> saveAll(entityStream: Publisher<S>): Flux<S> = Flux.from(entityStream).doOnNext { byId[it.id] = it }

    override fun findById(id: String): Mono<UserEntity> = Mono.justOrEmpty(byId[id])
    override fun findById(id: Publisher<String>): Mono<UserEntity> = Mono.from(id).flatMap { findById(it) }

    override fun existsById(id: String): Mono<Boolean> = Mono.just(byId.containsKey(id))
    override fun existsById(id: Publisher<String>): Mono<Boolean> = Mono.from(id).flatMap { existsById(it) }

    override fun findAll(): Flux<UserEntity> = Flux.fromIterable(byId.values)
    override fun findAllById(ids: Iterable<String>): Flux<UserEntity> = Flux.fromIterable(ids).flatMap { findById(it) }
    override fun findAllById(idStream: Publisher<String>): Flux<UserEntity> = Flux.from(idStream).flatMap { findById(it) }

    override fun count(): Mono<Long> = Mono.just(byId.size.toLong())

    override fun deleteById(id: String): Mono<Void> { byId.remove(id); return Mono.empty() }
    override fun deleteById(id: Publisher<String>): Mono<Void> = Mono.from(id).doOnNext { byId.remove(it) }.then()

    override fun delete(entity: UserEntity): Mono<Void> { byId.remove(entity.id); return Mono.empty() }
    override fun deleteAllById(ids: Iterable<String>): Mono<Void> { ids.forEach { byId.remove(it) }; return Mono.empty() }
    override fun deleteAll(entities: Iterable<UserEntity>): Mono<Void> { entities.forEach { byId.remove(it.id) }; return Mono.empty() }
    override fun deleteAll(entityStream: Publisher<out UserEntity>): Mono<Void> = Flux.from(entityStream).doOnNext { byId.remove(it.id) }.then()
    override fun deleteAll(): Mono<Void> { byId.clear(); return Mono.empty() }
}

class ApiFakeMoodRepository : MoodRepository {
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
}