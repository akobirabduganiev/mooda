package tech.nuqta.mooda.infrastructure.persistence.repository

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.persistence.entity.UserIdentityEntity

interface UserIdentityRepository : ReactiveCrudRepository<UserIdentityEntity, String> {
    fun findByProviderAndSubject(provider: String, subject: String): Mono<UserIdentityEntity>
    fun findByUserIdAndProvider(userId: String, provider: String): Mono<UserIdentityEntity>
}