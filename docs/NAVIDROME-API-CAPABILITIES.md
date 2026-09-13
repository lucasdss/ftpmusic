# Navidrome/Subsonic API — Capability Analysis vs Design & Implementation

**Generated:** 2026-06-27 | **Source:** Subsonic API v1.16.1 + Navidrome compatibility docs + OpenSubsonic  
**10 verification passes per capability**

---

## 1. Currently Implemented Endpoints (20 total)

| # | Endpoint | Used For | Callers |
|---|----------|----------|---------|
| 1 | `ping` | Server connection test | ServerConnectViewModel, ServerReconnectionService |
| 2 | `search3` | Search screen | SearchRepository → SearchViewModel |
| 3 | `getAlbum` | Album detail | AlbumRepository → AlbumDetailViewModel |
| 4 | `getArtist` | Artist detail | ArtistDetailViewModel |
| 5 | `getArtists` | Library Artists tab | LibraryViewModel |
| 6 | `getAlbumList2` | Library Albums, Home Recently Added | LibraryViewModel (3 call sites: newest/frequent/random) |
| 7 | `star` | Favorite track/album/artist | FavoriteRepository (3 call sites) |
| 8 | `unstar` | Unfavorite | FavoriteRepository (3 call sites) |
| 9 | `getStarred2` | Favorites list | FavoriteRepository (raw, not parsed into entities) |
| 10 | `scrobble` | Now playing + submission | ScrobbleService (2 call sites) |
| 11 | `getSimilarSongs2` | Instant mix / continuous playback | ScrobbleService |
| 12 | `savePlayQueue` | Persist queue to server | ScrobbleService → MediaService |
| 13 | `getPlayQueue` | ❌ UNUSED — defined, never called | None |
| 14 | `getAlbumInfo2` | ❌ UNUSED — defined, never called | None |
| 15 | `getGenres` | Home genre chips | LibraryViewModel (throttled sync) |
| 16 | `getSongsByGenre` | Genre detail screen | GenreDetailViewModel |
| 17 | `getPlaylists` | Library Playlists tab, playlist picker | LibraryViewModel, AlbumDetailViewModel |
| 18 | `getPlaylist` | Playlist detail | PlaylistDetailViewModel |
| 19 | `createPlaylist` | Create playlist dialog | LibraryViewModel |
| 20 | `updatePlaylist` | Add tracks to playlist | LibraryViewModel, AlbumDetailViewModel |
| 21* | `stream` | Audio playback (proxy) | PlaybackProxy (direct URL, not through Retrofit) |
| 22* | `getCoverArt` | Album/artist cover images | All screens (direct URL construction) |

*Not in SubsonicApi interface, called as raw URLs

---

## 2. Design-Referenced Capabilities — Implementation Status

| Design Feature | API Required | Implemented? | Gap |
|---------------|-------------|-------------|-----|
| **Lyrics** (Now Playing tab) | `getLyrics` / `getLyricsBySongId` (OpenSubsonic) | ❌ | Design shows "Lyrics Context" tab anchor + "real-time synchronized scrolling lyrics". Navidrome supports both embedded (.lrc/.txt) and OpenSubsonic structured lyrics. |
| **Star Ratings** (albums/tracks) | `setRating` + parse `userRating` from `getAlbum`/`search3` | ❌ | Design shows StarRating on album cards + track rows. No `getRating` endpoint exists; ratings embedded in entity responses. Need to parse `userRating` field from existing API responses and implement `setRating`. |
| **"Play Similar"** (Now Playing) | `getSimilarSongs2` + OpenSubsonic `sonicSimilarity` | ⚠️ PARTIAL | `getSimilarSongs2` is implemented. But design calls for "Play more like this" / "Play similar artists" — the current implementation uses a local fallback (same-artist → same-genre → random). Should use API first. |
| **Internet Radio** (Android Auto) | `getInternetRadioStations` | ❌ | Design references "Live Radio" for Android Auto browse tree. Navidrome supports full internet radio suite. |
| **Artist Images** | `getArtistInfo2` (last.fm) | ⚠️ WORKAROUND | Currently uses iTunes `CoverArtFallbackService`. `getArtistInfo2` returns last.fm images (small/medium/large) + biography. Would provide better artist images + bio text for Artist Detail screen. |
| **Album Info** | `getAlbumInfo2` | ❌ | Defined but NEVER called. Returns album notes, last.fm URL, musicBrainz ID. Could enrich Album Detail screen. |

---

## 3. Unimplemented Subsonic API Capabilities

### HIGH PRIORITY — Design Mock References These

#### 3.1 Lyrics (`getLyrics` + `getLyricsBySongId`)
**Design:** Now Playing mock shows a "Lyrics" tab with scrolling synchronized lyrics.  
**Server:** Navidrome supports both simple text lyrics (`getLyrics`) and OpenSubsonic structured lyrics (`getLyricsBySongId`) with timed lines, language codes, synced/unsynced flags.  
**Implementation gap:** No lyrics endpoint in SubsonicApi.kt. No lyrics UI on Now Playing.

#### 3.2 Similar Songs / "Play Similar" (`getSimilarSongs2` + OpenSubsonic `sonicSimilarity`)
**Design:** "Play more like this" / "Play similar artists" actions.  
**Implementation:** `getSimilarSongs2` IS in SubsonicApi and called by ScrobbleService. But the local fallback in `sonic_similarity_service` (same-artist → same-genre → random) acts as primary, not fallback. The API should be the primary source.  
**Gap:** OpenSubsonic `sonicSimilarity` extension (AI-driven server-side similarity) not checked or used.

#### 3.3 Internet Radio (`getInternetRadioStations`)
**Design:** "Live Radio" virtual folder for Android Auto browse tree.  
**Server:** Navidrome fully supports all 4 radio endpoints.  
**Implementation:** Not implemented.

#### 3.4 Star Ratings (`setRating` + parse `userRating`)
**Design:** StarRating component on album cards (Home, Library, Queue) and track rows.  
**API:** `setRating(id, rating: 1-5)` sets rating. No `getRating` endpoint — `userRating` is embedded in entity responses from `getAlbum`, `search3`, `getStarred2`.  
**Implementation:** No `setRating` endpoint. No `userRating` field on Track/Album models. `StarRating` UI component exists in design but not implemented.

### MEDIUM PRIORITY — Valuable Enhancements

#### 3.5 Artist Info (`getArtistInfo2`)
Returns: biography, last.fm images (small/medium/large), similar artists, musicBrainz ID.  
**Current workaround:** `CoverArtFallbackService` fetches from iTunes — works but returns only one size, no bio.  
**Gap:** Would enrich ArtistDetailScreen with biography + larger images + similar artists.

#### 3.6 Top Songs (`getTopSongs`)
Returns: top songs for an artist via last.fm.  
**Use:** Artist detail screen could show popular tracks beyond what's in the user's library.

#### 3.7 `getStarred2` Parsing
Returns artists, albums, AND songs with rich metadata in one call.  
**Current:** `FavoriteRepository.getStarred()` returns raw `Map<String, Any>` — NOT parsed into entities. Favorites screen uses local `trackDao.getStarred()` instead.  
**Gap:** Parsing `getStarred2` would give server-authoritative favorites list including albums + artists.

#### 3.8 `getGenres` + `byGenre` album list
**Current:** Genre screen uses `getSongsByGenre` for songs, then client-side groups into albums/artists.  
**Better:** `getAlbumList2(type=byGenre, genre="Rock")` returns albums directly — no client-side grouping needed.  
**Gap:** `AlbumListType` enum in the codebase is missing `byGenre` and `byYear`; includes invalid `highest` (not valid for `getAlbumList2`).

#### 3.9 Scan Status (`getScanStatus`)
Returns `scanning: true/false` with `count`.  
**Use:** Could show library scan progress indicator on Library screen.

### LOW PRIORITY — Niche/Admin Features

- **Bookmarks** (`getBookmarks`, `createBookmark`): Audiobook-oriented. Not music-focused.
- **Shares** (`getShares`, `createShare`): Social sharing. Requires server config `EnableSharing`.
- **Album Info** (`getAlbumInfo2`): Album notes/descriptions. Nice but not in design.

### NOT IMPLEMENTABLE — Navidrome Excludes

- **Video streaming**: Navidrome explicitly excludes video functionality.
- **Podcasts**: Navidrome's music-only focus suggests podcasts are unsupported.

---

## 4. OpenSubsonic Extensions

Navidrome supports OpenSubsonic extensions. Key ones relevant to ftpmusic:

| Extension | Purpose | Design Reference | Priority |
|-----------|---------|-----------------|----------|
| `songLyrics` | Structured synced lyrics (v1: line-level, v2: word-level karaoke) | Now Playing lyrics tab | HIGH |
| `sonicSimilarity` | AI-driven audio similarity on server | "Play Similar" button | HIGH |
| `formPost` | HTTP POST for large playlists (avoids URL limits) | Playlist sync | MEDIUM |
| `apiKeyAuth` | API key authentication (no password needed) | Security | LOW |
| `transcoding` | Server-side format info | Stream quality | LOW |

---

## 5. Model Gaps vs API Response Fields

Fields returned by Subsonic API but NOT mapped in Kotlin models:

| Entity | Missing Field | Source Endpoint | Used In Design? |
|--------|--------------|-----------------|-----------------|
| Track | `starred` (DateTime?) | `getStarred2`, `getAlbum`, `search3` | Yes — favorites |
| Track | `userRating` (Int? 1-5) | `getAlbum`, `search3` | Yes — star ratings |
| Track | `playCount` (Int?) | Multiple endpoints | No |
| Track | `musicBrainzId` (String?) | `getAlbum`, `search3` | No |
| Album | `starred` (DateTime?) | `getStarred2`, `getAlbumList2` | Yes — favorites |
| Album | `songCount` (Int?) | `getAlbumList2`, `getArtist` | No — already have |
| Artist | `biography` (String?) | `getArtistInfo2` | No |
| Artist | `largeImageUrl` (String?) | `getArtistInfo2` | Yes — artist images |

---

## 6. Priority Action Items

| # | Priority | Action | Effort |
|---|----------|--------|--------|
| 1 | 🔴 HIGH | Implement `getLyrics` / `getLyricsBySongId` + Lyrics tab on Now Playing | Medium |
| 2 | 🔴 HIGH | Parse `getSimilarSongs2` response properly, make it primary for "Play Similar" | Low |
| 3 | 🔴 HIGH | Implement `getInternetRadioStations` for Android Auto browse tree | Low |
| 4 | 🔴 HIGH | Implement `setRating` + parse `userRating` from entity responses + add StarRating UI | Medium |
| 5 | 🟡 MEDIUM | Implement `getArtistInfo2` for biography + high-res images | Low |
| 6 | 🟡 MEDIUM | Parse `getStarred2` into entities (artists + albums + tracks) | Low |
| 7 | 🟡 MEDIUM | Fix `AlbumListType` enum: add `byGenre`, `byYear`; remove invalid `highest` | Trivial |
| 8 | 🟡 MEDIUM | Check for `sonicSimilarity` extension + use if available | Low |
| 9 | 🟡 MEDIUM | Implement `getTopSongs` for artist detail enrichment | Low |
| 10 | 🟢 LOW | Implement `getScanStatus` for library scan progress | Low |
| 11 | 🟢 LOW | Remove or wire `getPlayQueue` and `getAlbumInfo2` (dead code) | Trivial |

---

## 7. Dead Endpoints in SubsonicApi.kt

Two endpoints defined but have ZERO callers:
- `getPlayQueue` (line 97-98): Restore play queue from server
- `getAlbumInfo2` (line 100-104): Album notes + last.fm images

These should either be wired or removed.
