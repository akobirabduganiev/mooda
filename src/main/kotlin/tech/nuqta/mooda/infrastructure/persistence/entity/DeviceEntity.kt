package tech.nuqta.mooda.infrastructure.persistence.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("devices")
data class DeviceEntity(
    @Id
    val id: String,
    @Column("device_id")
    val deviceId: String,
    @Column("user_id")
    val userId: String?,
    @Column("created_at")
    val createdAt: Instant = Instant.now(),
    @Column("last_seen_at")
    val lastSeenAt: Instant = Instant.now()
)