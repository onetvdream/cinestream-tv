package com.cinestream.tv

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ---- Models (Xtream Codes player_api.php) ----

data class Category(
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
)

data class LiveStream(
    @SerializedName("stream_id") val streamId: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
)

interface XtreamApi {
    @GET("player_api.php")
    suspend fun liveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories",
    ): List<Category>

    @GET("player_api.php")
    suspend fun liveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
    ): List<LiveStream>
}

// Holds the user's credentials and builds the API + stream URLs.
data class Account(val server: String, val username: String, val password: String) {
    // server normalised to scheme://host[:port] with no trailing slash
    val origin: String get() = server.trimEnd('/')

    fun api(): XtreamApi = Retrofit.Builder()
        .baseUrl("$origin/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(XtreamApi::class.java)

    fun liveUrl(streamId: Long): String = "$origin/live/$username/$password/$streamId.ts"
}
