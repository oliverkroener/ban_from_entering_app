package com.banfromentering.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BanDao {

    @Query("SELECT * FROM bans ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Ban>>

    @Query("SELECT * FROM bans WHERE id = :id")
    suspend fun get(id: Long): Ban?

    @Upsert
    suspend fun upsert(ban: Ban)

    @Delete
    suspend fun delete(ban: Ban)
}
