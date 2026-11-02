package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ScanHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT 500")
    fun getAllHistory(): Flow<List<ScanHistory>>

    @Query("SELECT * FROM scan_history WHERE kind = :kind ORDER BY timestamp DESC LIMIT 200")
    fun getHistoryByKind(kind: String): Flow<List<ScanHistory>>

    @Query("SELECT COUNT(*) FROM scan_history WHERE code = :code AND timestamp > :since")
    suspend fun countRecentUses(code: String, since: Long): Int

    /**
     * The codes of the plans bought most often, the most used first.
     *
     * Counted by code rather than by name: a plan renamed by its operator is
     * still the same plan, and a code is what the phone actually dialled. Ties
     * are broken by the most recent use, so habits that change are followed.
     * Only purchases count — asking what is left of a plan has its own kind.
     */
    @Query(
        "SELECT code FROM scan_history " +
            "WHERE kind = 'offer' AND code != '' AND timestamp > :since " +
            "GROUP BY code ORDER BY COUNT(*) DESC, MAX(timestamp) DESC LIMIT :limit"
    )
    suspend fun mostBoughtOfferCodes(since: Long, limit: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ScanHistory)

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM scan_history")
    suspend fun clear()
}
