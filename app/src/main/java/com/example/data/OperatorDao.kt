package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Operator
import kotlinx.coroutines.flow.Flow

@Dao
interface OperatorDao {
    @Query("SELECT * FROM operators")
    fun getAllOperators(): Flow<List<Operator>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperator(operator: Operator)

    @Update
    suspend fun updateOperator(operator: Operator)

    @Query("DELETE FROM operators WHERE id = :id")
    suspend fun deleteOperatorById(id: Int)
}
