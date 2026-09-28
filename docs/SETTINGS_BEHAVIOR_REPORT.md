# Settings Behavior Report

Caveman terse. Code truth after ADR-0044 settings wiring pass.

## Storage layers

| Layer | Backend | Role |
|-------|---------|------|
| SecureStorage | EncryptedSharedPreferences `ftpmusic_secure` | Server creds + most prefs |
| CastPreferences | SharedPreferences `ftpmusic_cast` | Cast from phone / HTTP cast |
| ftpmusic_sync | SharedPreferences | Sync metrics (read-mostly in Settings) |
| Room `custom_mixes` | SQLite | Custom Daily Mix recipes |

No DataStore. No ThemeMode. No download-quality pref.

## Control → persist → consumer

### Server Connection
| Control | Persist | Consumer |
|---------|---------|----------|
| URL/user/pass + Save | `KEY_URL/USERNAME/PASSWORD` via ServerConfigStore | OkHttp/auth |
| Test Connection | none | ServerProbe.ping only |

### Cache
| Control | Persist | Consumer |
|---------|---------|----------|
| Audio cache slider | `KEY_AUDIO_CACHE_MAX_BYTES` | AdjustableCacheEvictor (MediaModule) |
| Cover Art quota | `KEY_COVER_ART_QUOTA_MB` | CoverArtFallbackService + PreferenceBootstrap |
| Clear cache / downloads | filesystem | CacheService |

### Queue
| Control | Persist | Consumer |
|---------|---------|----------|
| History Size | `KEY_QUEUE_JOURNAL_CAP` | PlaybackManager (bootstrap + Settings) |
| Continuous Play | `KEY_CONTINUOUS_PLAY_ENABLED` | PlaybackManager |
| Overwrite Ask/Clean/Push | `KEY_QUEUE_OVERWRITE_BEHAVIOR` | PlaybackManager.overwriteBehavior() live read |

### Network / Cast / Downloads
| Control | Persist | Consumer |
|---------|---------|----------|
| Simulate Offline | `KEY_OFFLINE_MODE` | OfflineModeManager (FtpmusicApp) |
| Cast from Phone | `cast_from_phone` | MediaService, SubsonicMediaItemConverter |
| Casting to: | live PlayerHolder | display only |
| Use HTTP for Cast | `cast_use_http` | SubsonicMediaItemConverter |
| Wi-Fi Only downloads | `KEY_DOWNLOAD_MOBILE_DATA` (inverted UX) | DownloadManager (FtpmusicApp) |
| Auto-Download Playlists | `KEY_AUTO_DOWNLOAD_PLAYLISTS` | Library/Playlist VMs |

### Appearance
| Control | Persist | Consumer |
|---------|---------|----------|
| Prefer iTunes album art | `KEY_PREFER_ITUNES_ART` | CoverArtResolver |

Dark Mode / Accent Color stubs **removed**.

### Notifications / Home
| Control | Persist | Consumer |
|---------|---------|----------|
| Playback notifications | `KEY_PLAYBACK_NOTIFICATIONS` | MediaService lambda |
| Home/Favorites section toggles | `KEY_HOME_SHOW_*` | LibraryVM + FavoritesScreen on resume |

### Integrations — Last.fm
| Control | Persist | Consumer |
|---------|---------|----------|
| API key Save/Clear | `KEY_LASTFM_API_KEY` | LastFmService → ArtistDetail similar artists |

Scrobble path = Subsonic `ScrobbleService` (Navidrome user/pass). Server may forward to Last.fm — **not** this key.

### Custom HTTP Headers
| Control | Persist | Consumer |
|---------|---------|----------|
| Key/Value rows (max 5) | `KEY_CUSTOM_HEADERS` | CustomHeadersInterceptor (bootstrap) |

### Library Data actions
| Control | Effect |
|---------|--------|
| Profile (card + row) | navigate `profile` — local My Listening + Recently Played |
| Resync Library | syncing route |
| Rebuild Daily Mixes | rebuildmix |
| Custom Daily Mixes | customMixes |
| Sync Interval | `KEY_SYNC_INTERVAL_HOURS` + WorkManager |

## Cold-start hydration (PreferenceBootstrap)

Applied in `FtpmusicApp.onCreate` **before** workers:

1. OfflineModeManager.initialize
2. DownloadManager.allowMobileData
3. custom headers → CustomHeadersInterceptor
4. journal cap + continuous play → PlaybackManager
5. cover art quota → CoverArtFallbackService.maxCacheBytes (default **300MB**)
6. sync interval → WorkManager schedule

SettingsVM re-applies for UI state; bootstrap is process-death safety net.

## Edge cases

- Empty Last.fm key → Similar Artists empty/hidden
- Key saved mid-session → LastFmService reads key per call (no stale lazy)
- Offline + key → ArtistDetail skips Last.fm fetch
- Custom headers after kill → first OkHttp request includes them
- Profile nav: settings → profile → back; server drafts not auto-saved
- Cast device name: Settings refresh reads PlayerHolder.castDeviceName

## Dead / removed

- `KEY_QUOTA_MB` removed (never R/W)
- Dark Mode noop, Accent "Teal" static — removed
- ProfileScreen was orphan — now routed
