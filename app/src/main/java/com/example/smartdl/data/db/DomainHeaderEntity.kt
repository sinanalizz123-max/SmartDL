package com.example.smartdl.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "domain_headers")
data class DomainHeaderEntity(
    @PrimaryKey val domain: String,
    val userAgent: String?,
    val referer: String?,
    val cookies: String?,
    val updatedAt: Long
)
