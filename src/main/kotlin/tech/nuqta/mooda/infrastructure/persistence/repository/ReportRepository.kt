package tech.nuqta.mooda.infrastructure.persistence.repository

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.persistence.entity.ReportEntity

interface ReportRepository : ReactiveCrudRepository<ReportEntity, String> {
    fun findByMoodId(moodId: String): Flux<ReportEntity>
    fun findByIdAndMoodId(id: String, moodId: String): Mono<ReportEntity>
}