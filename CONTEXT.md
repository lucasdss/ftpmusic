# CONTEXT.md — ftpmusic Domain Glossary

## Core Concepts

- **Stream**: Playback directly from the remote Subsonic server. Data consumed, not persisted.
- **Urgent Window**: The next 3 tracks after the current one. Enqueued at priority 0 (always downloaded, no Wi-Fi/battery gating) so track advance never waits on the network. The REMAINDER of the queue is enqueued at priority 1, gated by the standard constraints. The whole queue is progressively cached this way (queue-always-cached). ExoPlayer's internal decode buffer holds the actual audio data for the current track; a mid-track network drop is bridged by the streamed-to-disk cache (CacheDataSink) plus the decode buffer. _Avoid_: Pre-buffer, RAM cache, in-memory audio buffer.
- **Auto-cache**: Tracks automatically saved to disk as they're streamed (LRU-evicted when storage quota is exceeded). No user action required.
- **Download**: User explicitly chose to persist a track, album, or playlist to local storage. Never evicted automatically. Shown with a checkmark badge in the UI.
- **Offline Mode**: User-toggled mode that forces all playback exclusively from Downloads + Auto-cache. Network calls blocked entirely.

## Internal Implementation Terms

- **Playback Cache** → Urgent Window (next-3 disk cache; supersedes the removed pre-buffer concept)
- **Rolling Cache** → Auto-cache (LRU-evicted disk storage, quota-managed)
- **Permanent Cache** → Download (user-initiated persistent storage, never auto-evicted)

## Cache Tracking & Warming

- **Play Count**: Number of times a track has been scrobbled (passed play threshold). Incremented atomically on scrobble insert. Used as the primary signal for "most played" ranking.
  _Avoid_: Listen count, hit count

- **Last Played**: Timestamp of the most recent scrobble for a track. Used for recency-based warming and as the LRU eviction key for auto-cache.
  _Avoid_: Last accessed, last listened

- **Cached At**: Timestamp when a track file was first written to the auto-cache or download storage. Not updated on repeated writes — records the first materialization only.

- **Cache Quota**: User-configurable maximum storage (in MB) allocated to the auto-cache. Default 1000MB. Applies only to auto-cached tracks; Downloads are not quota-limited.
  _Avoid_: Storage limit, disk budget

- **Cover Art Cache Quota**: User-configurable maximum storage (in MB) for cached cover art on disk. Default 300MB. Evicted via LRU by last-modified time when writing a new file exceeds the cap. Separate from audio Cache Quota.

## Metadata Sync

- **Metadata Sync**: Background pipeline that fetches and caches library structure (albums, artists, genres, album tracks) from the Subsonic server for offline availability. Runs on app start and periodically every 30 minutes. Not to be confused with playlist sync or lyrics sync.
  _Avoid_: Library sync, full sync

- **Sync Phase**: One stage of the metadata sync pipeline executed in fixed order: `albums` → `artists` → `genres` → `tracks` → `complete`. The `error` phase is terminal on failure. The UI uses the phase to conditionally label progress cards.
  _Avoid_: Stage, step

- **Sync Status**: Observable `StateFlow<SyncStatus>` emitted by `MetadataSyncWorker` during sync. Contains per-phase counters (albums synced / total, genres synced / total, etc.) and a terminal signal (`isRunning = false, phase ∈ {complete, error}`). Consumed directly by the UI — no separate UI-only state class.
  _Avoid_: Sync progress, sync state

- **Album Tracks Progress**: Count of albums whose tracks have been fetched and cached during the `tracks` sync phase. Shown as the "Albums w/ tracks" card during sync. Its total (`tracksTotal`) is the number of albums needing track data (not the number of tracks).
  _Avoid_: Tracks synced (refers to actual track count)

- **Track Count**: Cumulative number of individual tracks cached across all albums during metadata sync. Displayed on a separate "Tracks synced" card. Its total is only known at sync completion (after all albums are fetched).
  _Avoid_: Tracks total (ambiguous during sync phase)

## Favorites

- **Daily Mix**: User-managed recipe that generates a day-scoped tracklist (30–120 tracks, artist-diverse, weighted by play count + ratings). Each mix has a name, up to three filter dimensions (genres | decades | artists), and an optional auto-cache flag; max 20. Seeded once from the top-20 genres, then user-owned. Persisted in `custom_mixes` + `daily_mix(date, mix_id)` + `daily_mix_tracks`. Displayed as cards on the Home Screen.
  _Avoid_: Genre mix, auto-playlist, Daily Mix Genres

- **Mix Filters**: The composite pool a Daily Mix draws from. Within a dimension picks are ORed; non-empty dimensions are ANDed (Rock ∩ 90s); an empty dimension is a wildcard. Genres/decades are uncapped (picker pages 20 at a time); artists are searchable over the local library by id, plus an optional dynamic "all favorite artists" toggle. Disliked tracks are always excluded.
  _Avoid_: Mix source (single-kind, pre-v48)

- **Daily Mix Generation**: The process of building a mix's tracklist for today. Runs during metadata sync, on Home lazy load (ADR 0017), on Home refresh-all, and on detail refresh/source edit — all through `DailyMixRepository`. Seed-once: sync never mutates or re-creates mixes.
  _Avoid_: On-demand generation, lazy mix

- **Mix Cache Ownership**: `custom_mix_cache_tracks` rows recording which auto-cache mix asked for which track. Eviction on source edit/delete/toggle-off removes only tracks no other auto-cache mix owns and that are not explicit downloads. Enqueued at DownloadManager priority 2 (constrained, LRU-evictable).

- **Daily Mix Ratings**: Weighting system for track selection in Daily Mixes. Uses log-scale play count as the primary signal (`log₂(playCount)` — 1 play=weight 1, 16+ plays=weight 5) plus a user-rating bonus (`⌊rating / 2⌋`, max +2). Starred tracks receive a +2 bonus as a proxy for explicit preference. Combined weight is clamped to 1–5. Built via `DailyMixGenerator.buildWeightMap()`.
  _Avoid_: Score, preference weight
  _Avoid_: Image cache limit, artwork disk budget

- **Streaming Floor**: Minimum 100MB (or 10% of quota) reserved exclusively for natural stream-to-cache accumulation during playback. Cache warming must not consume this headroom.
  _Avoid_: Buffer reserve, streaming headroom

- **Cache Warming**: Proactive pre-caching of tracks before the user requests them. Three phases:
  - **Phase 1 (passive batch)**: On Wi-Fi + charging, pre-caches a smart mix of starred, top-played, and recently-played tracks. Initially seeded from server data (starred albums, frequent/recent album lists) until local play history accumulates.
  - **Phase 2 (opportunistic)**: After a track finishes playing, pre-caches the next likely track if quota headroom exists.
  - **Phase 3 (smart processing)**: Future phase on Wi-Fi + charging for advanced analysis (album completion, sonic similarity radio).
  _Avoid_: Pre-cache, predictive download

## Server Ecosystem

- **Subsonic Server**: Any self-hosted server exposing the Subsonic/OpenSubsonic REST API (Navidrome, Gonic, Airsonic-Advanced, Ampache, etc.)
- **Scrobble**: A record of a completed track listen, queued locally and flushed to the server when connectivity permits.
- **Transcoding**: Server-side audio format/bitrate conversion using FFmpeg, requested via `format` and `maxBitRate` API parameters.

## Platform Integration Terms

- **Audio Focus**: OS-level coordination determining which app may output audio at a given moment.
- **Media Session**: OS-level representation of the player state, surfaced in notifications, lock screens, and automotive head units.
- **Cast Hand-off**: The Google Cast protocol where the mobile device transfers a URL to the receiver, which then streams independently.
- **Cast URL Resolution (3-tier)**: Strategy for resolving what URL to send to the Cast receiver. Tier 1: fresh remote URL (zero battery, default). Tier 2: remote URL even for cached tracks (avoids phone streaming). Tier 3: Local proxy — phone streams cached file to receiver over LAN (fallback, user-opt-in, high battery).

## Local Proxy

- **Local Proxy**: Embedded HTTP server on `127.0.0.1:9000` that serves CACHED audio to the Cast receiver over LAN (Cast tier 3). Binds for the full MediaService lifecycle. Local playback does NOT use the proxy — ExoPlayer streams through the shared SimpleCache via CacheDataSource with an offline-aware upstream. _Avoid_: Local server, embedded proxy, stream interceptor.

- **Proxy Stream**: A URL served by the local proxy in the form `http://127.0.0.1:9000/stream?id=<trackId>`. Serves the cached span file with correct Range support (bounded, suffix and open-ended ranges → 206; beyond-EOF → 416; cache miss → 404 so the receiver falls back to its direct server URL). _Avoid_: Proxy URL, local stream URL.

- **Proxy Cache Hit**: The track file exists in auto-cache or downloads. Proxy serves directly from disk with Range support for seeking — no network call. _Avoid_: Local hit, disk serve.

- **Proxy Cache Miss**: The track file is not cached. The proxy returns 404 and the Cast receiver falls back to the direct Subsonic server URL (Tier 1). _Avoid_: Remote fetch, pass-through.

- **Offline-Aware Upstream**: Local playback's HTTP upstream fails fast (IOException) while software offline mode is on — uncached tracks error in milliseconds and the auto-skip guard advances the queue; cached tracks are served from disk without ever touching the upstream. _Avoid_: Blind network attempts while offline.

- **Certificate Trust**: Configurable SSL trust for the outbound connection to the Subsonic server. Default: accept-on-first-connect (user confirms server identity during setup). Toggleable in settings for self-signed certs vs system trust store. _Avoid_: SSL bypass, trust all.

## Cast Ecosystem
- **Cast Session**: Active connection between Sender (phone) and Receiver (Cast hardware). Managed via CastPlayer wrapping ExoPlayer (local) + RemoteCastPlayer (Cast SDK). On connect, CastPlayer pauses local ExoPlayer and routes all Player commands to the Cast receiver. On disconnect, restores local playback state. _Avoid_: CastContext.sessionManager (now internal to CastPlayer).
- **RemoteCastPlayer**: Media3 component that wraps the Cast SDK's RemoteMediaClient. Handles device discovery, session lifecycle, and remote playback control. Created via RemoteCastPlayer.Builder and passed to CastPlayer. Never used directly by app code.
- **MediaItemConverter**: Interface that converts Media3 MediaItems (with local proxy URLs) to Cast MediaQueueItems (with direct Subsonic server URLs). Our impl: SubsonicMediaItemConverter. Handles http/https preference and castFromPhone LAN proxy tier. _Avoid_: URL extraction, Cast URL builder.
- **Cast Hand-off**: The Google Cast protocol where the mobile device transfers a URL to the receiver, which then streams independently.
- **Cast URL Resolution (3-tier)**: Strategy for resolving what URL to send to the Cast receiver. Implemented in SubsonicMediaItemConverter. Tier 1: direct server URL with optional HTTP downgrade (default, zero battery). Tier 2: N/A (removed — was same as Tier 1). Tier 3: LAN proxy — phone streams cached file to receiver over LAN (fallback, user-opt-in via castFromPhone, high battery).
- **Cast Button**: The MediaRouteButton in the app bar that opens device discovery and session connection.
- **Cast Mini Controller**: Bottom-of-screen bar showing current track during a Cast session. Distinct from Mini Player (local playback). Only visible during active Cast sessions.
- **Now Playing (Cast)**: Full-screen Cast playback UI triggered from the Cast Mini Controller. Parallel "mode" to the local Now Playing screen — both share the same UI real estate.
- **Cast Receiver UI**: Hosted HTML/JS app rendered on Cast devices with displays. Provides branded UI for audio metadata. Not needed for screenless speakers (Nest Audio).
- **Browse Tree**: The hierarchical folder structure served to Android Auto / CarPlay templates for driver-safe navigation.
- **Playlist**: User-curated list of tracks synced with the Subsonic server. Local-first: edits write to Drift immediately, background worker flushes to server. Conflicted if server rejects a change (user must reconcile).
- **Queue**: The transient playback queue (now-playing order). Distinct from Playlists. Stored in `queue_items` table.
