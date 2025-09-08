package tech.nuqta.mooda.infrastructure.persistence.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("user_identities")
data class UserIdentityEntity(
    @Id
    val id: String,
    @Column("user_id")
    val userId: String,
    val provider: String, // e.g. GOOGLE
    val subject: String
)