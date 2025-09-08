package tech.nuqta.mooda.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

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
}