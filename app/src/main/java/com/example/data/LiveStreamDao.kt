package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LiveStreamDao {
    @Query("SELECT * FROM live_streams")
    suspend fun getAllStreams(): List<LiveStreamEntity>

    @Query("SELECT COUNT(*) FROM live_streams")
    suspend fun getStreamCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(streams: List<LiveStreamEntity>)

    @Query("DELETE FROM live_streams")
    suspend fun clearAll()
}
