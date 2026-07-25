package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.MessageTemplate
import com.example.data.model.SimProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface SimProfileDao {
    @Query("SELECT * FROM sim_profiles")
    fun getAll(): Flow<List<SimProfile>>

    @Query("SELECT * FROM sim_profiles")
    suspend fun getAllOnce(): List<SimProfile>

    @Query("SELECT * FROM sim_profiles WHERE subscriptionId = :subscriptionId LIMIT 1")
    suspend fun get(subscriptionId: Int): SimProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SimProfile)

    @Query("UPDATE sim_profiles SET isPreferred = 0 WHERE operatorName = :operatorName COLLATE NOCASE")
    suspend fun clearPreferredFor(operatorName: String)

    @Query("DELETE FROM sim_profiles WHERE subscriptionId = :subscriptionId")
    suspend fun delete(subscriptionId: Int)
}

@Dao
interface MessageTemplateDao {
    @Query("SELECT * FROM message_templates ORDER BY sortOrder ASC, id ASC")
    fun getAll(): Flow<List<MessageTemplate>>

    @Query("SELECT COUNT(*) FROM message_templates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: MessageTemplate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<MessageTemplate>)

    @Update
    suspend fun update(template: MessageTemplate)

    @Delete
    suspend fun delete(template: MessageTemplate)
}
