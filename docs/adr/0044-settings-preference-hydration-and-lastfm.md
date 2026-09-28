# ADR 0044 — Settings Preference Hydration and Last.fm API Key

Date: 2026-09-28
Status: Accepted
Related: ADR 0022 (offline), ADR 0033 (server config), ADR 0009 (scrobble)

## Context

Several Settings prefs persisted to SecureStorage but were only applied when
`SettingsViewModel` initialized (user opens Settings). After process death:

- Custom HTTP headers stayed empty in `CustomHeadersInterceptor`
- Queue journal cap / continuous play stayed PlaybackManager defaults
- Cover art quota stayed `CoverArtFallbackService` field default (500MB) vs UI default 300MB

Separately, `KEY_LASTFM_API_KEY` was read by `LastFmService` but never written from UI.
`LastFmService` also used ad-hoc `SecureStorage(context)` + `by lazy` apiKey, so a
mid-session Settings save could not take effect.

Dark Mode / Accent Color were UI stubs (noop / static). ProfileScreen existed
but had no NavHost route.

## Decision

1. **PreferenceBootstrap** — single injectable helper called from `FtpmusicApp.onCreate`
   after offline restore. Hydrates headers, journal, continuous play, cover quota.
2. **Last.fm** — Settings section stores API key only. Powers `artist.getSimilar`
   for Artist Detail. Inject Hilt `SecureStorage`; read key per fetch (no stale lazy).
3. **Scrobble stays Subsonic** — `ScrobbleService` / Navidrome credentials. Do not add
   Last.fm shared secret or session for client scrobble.
4. **Remove** Dark Mode + Accent stubs. **Wire** Profile navigation.
5. Promote `custom_headers` / `auto_download_playlists` to SecureStorage constants;
   remove unused `KEY_QUOTA_MB`.

## Consequences

- Prefs survive process death without visiting Settings.
- Similar Artists work after user pastes free Last.fm API key.
- Cover quota default aligned to 300MB in service + Coil bootstrap path.
- Clear product split: Last.fm key ≠ play scrobbling.
