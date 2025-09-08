package tech.nuqta.mooda.infrastructure.persistence.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.time.LocalDate

@Table("moods")
data class MoodEntity(
    @Id
    val id: String,
    @Column("user_id")
    val userId: String?,
    @Column("device_id")
    val deviceId: String,
    @Column("mood_type")
    val moodType: String,
    val country: String?,
    val locale: String?,
    val day: LocalDate,
    @Column("created_at")
    val createdAt: Instant = Instant.now()
)