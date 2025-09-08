package tech.nuqta.mooda.api.controller

import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.persistence.repository.MoodRepository
import java.time.Instant

@RestController
@RequestMapping("/api/v1/me")
class MeController(private val moodRepository: MoodRepository) {
    data class MeResponse(
        val userId: String,
        val email: String? = null,
        val providers: List<String> = listOf("GOOGLE"),
        val createdAt: Instant = Instant.EPOCH
    )

    data class MeMoodsItem(val day: String, val moodType: String)
    data class MeMoodsResponse(val items: List<MeMoodsItem>)

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun me(auth: Mono<Authentication>): Mono<MeResponse> =
        auth.map { MeResponse(userId = it.name ?: "unknown") }

    @GetMapping("/moods", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun myMoods(
        auth: Mono<Authentication>,
        @RequestParam(name = "days", required = false, defaultValue = "7") daysParam: Int?
    ): Mono<MeMoodsResponse> {
        val daysRaw = daysParam ?: 7
        val days = when {
            daysRaw < 0 -> 0
            daysRaw > 31 -> 31
            else -> daysRaw
        }
        if (days == 0) {
            // Still require auth; map to empty response without touching repository
            return auth.map { MeMoodsResponse(items = emptyList()) }
        }
        return auth.flatMapMany { authentication ->
            val userId = authentication.name
            moodRepository.findByUserIdOrderByDayDesc(userId)
                .take(days.toLong())
        }.map { entity ->
            MeMoodsItem(day = entity.day.toString(), moodType = entity.moodType)
        }.collectList().map { list -> MeMoodsResponse(items = list) }
    }
}