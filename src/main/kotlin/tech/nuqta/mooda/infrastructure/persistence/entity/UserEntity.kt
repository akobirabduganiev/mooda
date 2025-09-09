package tech.nuqta.mooda.infrastructure.persistence.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("users")
data class UserEntity(
    @Id
    val id: String,
    val email: String?,
    @Column("first_name")
    val firstName: String? = null,
    @Column("last_name")
    val lastName: String? = null,
    val country: String? = null,
    @Column("created_at")
    val createdAt: Instant = Instant.now()
)