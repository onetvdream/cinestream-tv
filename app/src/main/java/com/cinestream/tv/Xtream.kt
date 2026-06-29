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

data class VodStream(
    @SerializedName("stream_id") val streamId: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("container_extension") val ext: String? = null,
    @SerializedName("rating") val rating: String? = null,
)

data class Series(
    @SerializedName("series_id") val seriesId: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("rating") val rating: String? = null,
)

data class Episode(
    @SerializedName("id") val id: String? = null,
    @SerializedName("container_extension") val ext: String? = null,
)

data class SeriesInfo(
    @SerializedName("episodes") val episodes: Map<String, List<Episode>>? = null,
)

interface XtreamApi {
    @GET("player_api.php")
    suspend fun liveCategories(@Query("username") u: String, @Query("password") p: String, @Query("action") a: String = "get_live_categories"): List<Category>

    @GET("player_api.php")
    suspend fun liveStreams(@Query("username") u: String, @Query("password") p: String, @Query("action") a: String = "get_live_streams"): List<LiveStream>

    @GET("player_api.php")
    suspend fun vodCategories(@Query("username") u: String, @Query("password") p: String, @Query("action") a: String = "get_vod_categories"): List<Category>

    @GET("player_api.php")
    suspend fun vodStreams(@Query("username") u: String, @Query("password") p: String, @Query("action") a: String = "get_vod_streams"): List<VodStream>

    @GET("player_api.php")
    suspend fun seriesCategories(@Query("username") u: String, @Query("password") p: String, @Query("action") a: String = "get_series_categories"): List<Category>

    @GET("player_api.php")
    suspend fun series(@Query("username") u: String, @Query("password") p: String, @Query("action") a: String = "get_series"): List<Series>

    @GET("player_api.php")
    suspend fun seriesInfo(@Query("username") u: String, @Query("password") p: String, @Query("series_id") id: Long, @Query("action") a: String = "get_series_info"): SeriesInfo
}

// Holds the user's credentials and builds the API + stream URLs.
data class Account(val server: String, val username: String, val password: String) {
    val origin: String get() = server.trimEnd('/')

    fun api(): XtreamApi = Retrofit.Builder()
        .baseUrl("$origin/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(XtreamApi::class.java)

    fun liveUrl(streamId: Long): String = "$origin/live/$username/$password/$streamId.ts"
    fun vodUrl(streamId: Long, ext: String?): String = "$origin/movie/$username/$password/$streamId.${ext ?: "mp4"}"
    fun episodeUrl(episodeId: String, ext: String?): String = "$origin/series/$username/$password/$episodeId.${ext ?: "mp4"}"
}
