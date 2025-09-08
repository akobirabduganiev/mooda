package tech.nuqta.mooda.infrastructure.persistence.repository

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tech.nuqta.mooda.infrastructure.persistence.entity.DeviceEntity

interface DeviceRepository : ReactiveCrudRepository<DeviceEntity, String> {
    fun findByDeviceId(deviceId: String): Mono<DeviceEntity>
    fun findByUserId(userId: String): Flux<DeviceEntity>
}