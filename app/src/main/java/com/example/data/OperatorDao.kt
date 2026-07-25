package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Operator
import kotlinx.coroutines.flow.Flow

@Dao
interface OperatorDao {
    @Query("SELECT * FROM operators ORDER BY sortOrder ASC, name ASC")
    fun getAllOperators(): Flow<List<Operator>>

    @Query("SELECT * FROM operators ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllOperatorsOnce(): List<Operator>

    @Query("SELECT * FROM operators WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Operator?

    @Query("SELECT * FROM operators WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): Operator?

    @Query("SELECT COUNT(*) FROM operators")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperator(operator: Operator): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(operators: List<Operator>)

    @Update
    suspend fun updateOperator(operator: Operator)

    @Delete
    suspend fun deleteOperator(operator: Operator)

    @Query("DELETE FROM operators WHERE id = :id")
    suspend fun deleteOperatorById(id: Int)
}
