package tech.nuqta.mooda.infrastructure.persistence.repository

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.persistence.entity.UserEntity

interface UserRepository : ReactiveCrudRepository<UserEntity, String> {
    fun findByEmail(email: String): Mono<UserEntity>
}