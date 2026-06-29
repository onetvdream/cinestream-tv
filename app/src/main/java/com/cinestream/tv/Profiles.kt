package com.cinestream.tv

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Profile(val id: String, val name: String, val color: Long)

// Palette for profile avatars (no network needed).
val PROFILE_COLORS = listOf(0xFFDC2626, 0xFF2563EB, 0xFF16A34A, 0xFF9333EA, 0xFFEA580C, 0xFF0891B2)

private const val PREFS = "cinestream"
private val gson = Gson()

fun loadProfiles(ctx: Context): List<Profile> {
    val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("profiles", null) ?: return emptyList()
    return try {
        gson.fromJson(json, object : TypeToken<List<Profile>>() {}.type) ?: emptyList()
    } catch (e: Exception) { emptyList() }
}

fun saveProfiles(ctx: Context, list: List<Profile>) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString("profiles", gson.toJson(list)).apply()
}

fun loadActiveProfile(ctx: Context): String? =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("activeProfile", null)

fun saveActiveProfile(ctx: Context, id: String?) {
    val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
    if (id == null) e.remove("activeProfile") else e.putString("activeProfile", id)
    e.apply()
}

fun loadLang(ctx: Context): String =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", "en") ?: "en"

fun saveLang(ctx: Context, lang: String) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lang", lang).apply()
}
