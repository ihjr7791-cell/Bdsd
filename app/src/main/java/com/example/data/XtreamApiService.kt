package com.example.data

import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamApiService {
    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams"
    ): List<LiveStreamEntity>
}
