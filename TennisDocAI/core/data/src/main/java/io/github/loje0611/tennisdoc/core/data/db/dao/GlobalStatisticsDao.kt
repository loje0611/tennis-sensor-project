package io.github.loje0611.tennisdoc.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.loje0611.tennisdoc.core.data.db.entity.GlobalStatisticsEntity

@Dao
interface GlobalStatisticsDao {

    @Query("SELECT * FROM global_statistics WHERE categoryKey = :categoryKey LIMIT 1")
    suspend fun getByCategory(categoryKey: String): GlobalStatisticsEntity?

    @Upsert
    suspend fun upsert(entity: GlobalStatisticsEntity)
}
