package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Offer
import kotlinx.coroutines.flow.Flow

@Dao
interface OfferDao {
    @Query("SELECT * FROM offers ORDER BY operatorName ASC, category ASC, sortOrder ASC, id ASC")
    fun getAllOffers(): Flow<List<Offer>>

    @Query("SELECT * FROM offers WHERE isFavorite = 1 ORDER BY operatorName ASC, sortOrder ASC")
    fun getFavorites(): Flow<List<Offer>>

    @Query("SELECT COUNT(*) FROM offers")
    suspend fun count(): Int

    @Query("SELECT * FROM offers WHERE operatorName = :operator COLLATE NOCASE")
    suspend fun getForOperator(operator: String): List<Offer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(offer: Offer): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(offers: List<Offer>)

    @Update
    suspend fun update(offer: Offer)

    @Delete
    suspend fun delete(offer: Offer)

    @Query("DELETE FROM offers WHERE isCustom = 0")
    suspend fun deleteBuiltIn()

    @Query("UPDATE offers SET operatorName = :newName WHERE operatorName = :oldName COLLATE NOCASE")
    suspend fun renameOperator(oldName: String, newName: String)
}
