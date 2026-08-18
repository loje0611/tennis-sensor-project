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

    @Query("UPDATE lab_raw_records SET videoPath = :videoPath WHERE id = :id")
    suspend fun updateVideoPath(id: Long, videoPath: String?)

    @Query("SELECT * FROM lab_raw_records WHERE videoPath IS NOT NULL ORDER BY timestampMillis ASC")
    suspend fun getRecordsWithVideoAsc(): List<LabRawRecordEntity>

    @Query("SELECT COUNT(*) FROM lab_raw_records WHERE videoPath IS NOT NULL")
    fun observeVideoRecordCount(): Flow<Int>

    @Query("UPDATE lab_raw_records SET videoPath = null WHERE videoPath = :videoPath")
    suspend fun clearVideoPathByPath(videoPath: String)

    @Query("UPDATE lab_raw_records SET videoPath = null")
    suspend fun clearAllVideoPaths()
}
