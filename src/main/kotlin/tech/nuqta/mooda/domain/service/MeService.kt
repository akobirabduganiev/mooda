package tech.nuqta.mooda.domain.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.persistence.repository.MoodRepository

@Service
class MeService(
    private val moodRepository: MoodRepository
) {
    data class MeResponse(
        val userId: String,
        val email: String? = null,
        val providers: List<String> = listOf("GOOGLE")
    )

    data class MeMoodsItem(val day: String, val moodType: String)
    data class MeMoodsResponse(val items: List<MeMoodsItem>)

    fun getProfile(userId: String): Mono<MeResponse> = Mono.just(MeResponse(userId = userId))

    fun getMoods(userId: String, daysParam: Int?): Mono<MeMoodsResponse> {
        val daysRaw = daysParam ?: 7
        val days = when {
            daysRaw < 0 -> 0
            daysRaw > 31 -> 31
            else -> daysRaw
        }
        if (days == 0) return Mono.just(MeMoodsResponse(items = emptyList()))

        val repo = moodRepository ?: return Mono.just(MeMoodsResponse(items = emptyList()))
        return repo.findByUserIdOrderByDayDesc(userId)
            .take(days.toLong())
            .map { entity -> MeMoodsItem(day = entity.day.toString(), moodType = entity.moodType) }
            .collectList()
            .map { list -> MeMoodsResponse(items = list) }
    }
}
