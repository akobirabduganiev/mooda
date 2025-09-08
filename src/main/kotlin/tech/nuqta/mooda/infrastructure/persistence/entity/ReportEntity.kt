package tech.nuqta.mooda.infrastructure.persistence.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("reports")
data class ReportEntity(
    @Id
    val id: String,
    @Column("mood_id")
    val moodId: String?,
    val reason: String,
    @Column("created_at")
    val createdAt: Instant = Instant.now()
)