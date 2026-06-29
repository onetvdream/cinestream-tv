# CineStream TV

Native Android TV app (Kotlin + Jetpack Compose for TV) for Xtream Codes IPTV.
No WebView — the D-pad, focus and list rendering are handled natively by Android.

## Build
GitHub Actions builds a debug APK on every push and publishes it to the
**latest** release. Download: `releases/latest/download/cinestream-tv.apk`.

## Status
Phase 1: Xtream login → live channels grid → ExoPlayer playback.
Next: VOD & Series (hero/details), EPG, profiles, favorites, search, i18n.
