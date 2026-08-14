package io.github.loje0611.tennisdoc.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.loje0611.tennisdoc.core.data.db.entity.LabRawRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabRawRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: LabRawRecordEntity): Long

    @Query("SELECT * FROM lab_raw_records WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    fun getRecordsBySessionId(sessionId: String): Flow<List<LabRawRecordEntity>>

    @Query("SELECT * FROM lab_raw_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): LabRawRecordEntity?

    @Query("DELETE FROM lab_raw_records WHERE sessionId = :sessionId")
    suspend fun deleteRecordsBySessionId(sessionId: String): Int
}
