package com.cinestream.tv

// Lightweight i18n matching the web app's 6 languages.
val LANGUAGES = listOf(
    "en" to "English",
    "fr" to "Français",
    "es" to "Español",
    "de" to "Deutsch",
    "it" to "Italiano",
    "ar" to "العربية",
)

private val STRINGS: Map<String, Map<String, String>> = mapOf(
    "en" to mapOf(
        "signIn" to "Sign in with Xtream Codes", "server" to "Server URL", "username" to "Username",
        "password" to "Password", "connect" to "Connect", "fillAll" to "Please fill in all fields",
        "whosWatching" to "Who's watching?", "addProfile" to "Add Profile", "manage" to "Manage Profiles",
        "done" to "Done", "live" to "Live TV", "movies" to "Movies", "series" to "Series",
        "play" to "Play", "loading" to "Loading…", "name" to "Name", "save" to "Save", "cancel" to "Cancel",
        "pressOk" to "Press OK to play", "switchPlaylist" to "Switch Playlist",
    ),
    "fr" to mapOf(
        "signIn" to "Connexion avec Xtream Codes", "server" to "URL du serveur", "username" to "Nom d'utilisateur",
        "password" to "Mot de passe", "connect" to "Se connecter", "fillAll" to "Veuillez remplir tous les champs",
        "whosWatching" to "Qui regarde ?", "addProfile" to "Ajouter un profil", "manage" to "Gérer les profils",
        "done" to "Terminé", "live" to "TV en Direct", "movies" to "Films", "series" to "Séries",
        "play" to "Lecture", "loading" to "Chargement…", "name" to "Nom", "save" to "Enregistrer", "cancel" to "Annuler",
        "pressOk" to "Appuyez sur OK pour lire", "switchPlaylist" to "Changer de playlist",
    ),
    "es" to mapOf(
        "signIn" to "Inicia sesión con Xtream Codes", "server" to "URL del servidor", "username" to "Usuario",
        "password" to "Contraseña", "connect" to "Conectar", "fillAll" to "Por favor completa todos los campos",
        "whosWatching" to "¿Quién está viendo?", "addProfile" to "Añadir perfil", "manage" to "Administrar perfiles",
        "done" to "Hecho", "live" to "TV en Vivo", "movies" to "Películas", "series" to "Series",
        "play" to "Reproducir", "loading" to "Cargando…", "name" to "Nombre", "save" to "Guardar", "cancel" to "Cancelar",
        "pressOk" to "Pulsa OK para reproducir", "switchPlaylist" to "Cambiar lista",
    ),
    "de" to mapOf(
        "signIn" to "Mit Xtream Codes anmelden", "server" to "Server-URL", "username" to "Benutzername",
        "password" to "Passwort", "connect" to "Verbinden", "fillAll" to "Bitte alle Felder ausfüllen",
        "whosWatching" to "Wer schaut?", "addProfile" to "Profil hinzufügen", "manage" to "Profile verwalten",
        "done" to "Fertig", "live" to "Live TV", "movies" to "Filme", "series" to "Serien",
        "play" to "Abspielen", "loading" to "Laden…", "name" to "Name", "save" to "Speichern", "cancel" to "Abbrechen",
        "pressOk" to "OK drücken zum Abspielen", "switchPlaylist" to "Playlist wechseln",
    ),
    "it" to mapOf(
        "signIn" to "Accedi con Xtream Codes", "server" to "URL del server", "username" to "Nome utente",
        "password" to "Password", "connect" to "Connetti", "fillAll" to "Compila tutti i campi",
        "whosWatching" to "Chi sta guardando?", "addProfile" to "Aggiungi profilo", "manage" to "Gestisci profili",
        "done" to "Fatto", "live" to "Live TV", "movies" to "Film", "series" to "Serie TV",
        "play" to "Riproduci", "loading" to "Caricamento…", "name" to "Nome", "save" to "Salva", "cancel" to "Annulla",
        "pressOk" to "Premi OK per riprodurre", "switchPlaylist" to "Cambia playlist",
    ),
    "ar" to mapOf(
        "signIn" to "تسجيل الدخول عبر Xtream Codes", "server" to "رابط الخادم", "username" to "اسم المستخدم",
        "password" to "كلمة المرور", "connect" to "اتصال", "fillAll" to "يرجى ملء جميع الحقول",
        "whosWatching" to "من يشاهد؟", "addProfile" to "إضافة ملف", "manage" to "إدارة الملفات",
        "done" to "تم", "live" to "البث المباشر", "movies" to "أفلام", "series" to "مسلسلات",
        "play" to "تشغيل", "loading" to "جار التحميل…", "name" to "الاسم", "save" to "حفظ", "cancel" to "إلغاء",
        "pressOk" to "اضغط OK للتشغيل", "switchPlaylist" to "تبديل القائمة",
    ),
)

class Strings(val lang: String) {
    private val map = STRINGS[lang] ?: STRINGS["en"]!!
    private val fallback = STRINGS["en"]!!
    fun t(key: String): String = map[key] ?: fallback[key] ?: key
}
