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
import tech.nuqta.mooda.infrastructure.persistence.repository.MoodRepository
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiTests {

    @Autowired
    lateinit var client: WebTestClient

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
    fun `auth google with TEST returns token and me works`() {
        val tokenMap = client.post()
            .uri("/api/v1/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("idToken" to "TEST"))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody as Map<*, *>

        val token = tokenMap["accessToken"] as String
        assertThat(token).isNotBlank

        client.get()
            .uri("/api/v1/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").value<String> { v -> assertThat(v).startsWith("u-test-subject") }
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
    fun `auth google audience mismatch returns problem code`() {
        client.post()
            .uri("/api/v1/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("idToken" to "TEST_AUD_MISMATCH"))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("oidc_audience_mismatch")
    }

    @Test
    fun `auth google invalid token returns problem code`() {
        client.post()
            .uri("/api/v1/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("idToken" to "BAD"))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("invalid_google_token")
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
        val tokenMap = client.post()
            .uri("/api/v1/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("idToken" to "TEST"))
            .exchange()
            .expectStatus().isOk
            .expectBody(Map::class.java)
            .returnResult()
            .responseBody as Map<*, *>
        val token = tokenMap["accessToken"] as String

        client.get()
            .uri("/api/v1/me/moods?days=0")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items").isArray
            .jsonPath("$.items.length()").isEqualTo(0)
    }

}