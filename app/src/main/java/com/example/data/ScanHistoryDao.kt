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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ScanHistory)

    @Query("DELETE FROM scan_history")
    suspend fun clear()
}
