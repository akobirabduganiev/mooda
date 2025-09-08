package tech.nuqta.mooda.infrastructure.persistence.repository

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.persistence.entity.MoodEntity
import java.time.LocalDate

interface MoodRepository : ReactiveCrudRepository<MoodEntity, String> {
    fun findByUserIdAndDay(userId: String, day: LocalDate): Mono<MoodEntity>
    fun findByDeviceIdAndDay(deviceId: String, day: LocalDate): Mono<MoodEntity>
    fun findByUserIdOrderByDayDesc(userId: String): Flux<MoodEntity>
    fun findByDay(day: LocalDate): Flux<MoodEntity>
}