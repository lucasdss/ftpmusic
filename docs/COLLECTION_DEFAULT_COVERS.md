# Collection default covers — Nano Banana prompt pack

Offline generation only. Assets land in
`compose/app/src/main/assets/collection_defaults/{packId}/…`.
Current tree ships **style-tinted placeholders**; replace with Gemini Nano Banana
(`gemini-nano-banana-2.1`) outputs using the prompts below. SynthID watermarks expected.

## Packs (9)

| id | Label |
|---|---|
| anime | Anime |
| pixel48 | Pixel 48 (48×48 → NN upscale) |
| futuristic | Futuristic |
| vintage | Vintage |
| watercolor | Watercolor |
| bauhaus | Bauhaus |
| clay | Clay |
| risograph | Risograph |
| noir | Noir |

## Shared base

```text
Square 1:1 music app collection cover. Not a photo of a real album.
No readable text, no logos, no watermarks, no celebrity likenesses, no brand marks.
Centered focal subject with quiet margins for rounded crop.
Keep the SAME subject motif family as other packs for this slug (recognizable across styles).
```

Append the pack style modifier from ADR 0151 / plan section 2b, then the subject focus.

## Genres (Navidrome live 2026-11-05)

Rock, Pop, MPB, Metal, Rap, Hip Hop, Classical, R&B, Latin, Folk, Axé, Dance,
Electronic, New Age, Soul, Reggae, Bossa Nova, Alternative, Samba, Country, Music,
Soundtrack, instrumental, Punk, Forró, Hard Rock, Jazz, Contemporary R&B, Indie,
Heavy, Hip-Hop, Thrash Metal.

Slug map: see `assets/collection_defaults/catalog.json`.

## Daily Mixes

`{TopGenre} Mix` for top 20 genres → `mixes/{slug}_mix.webp` with Daily Mix framing:

```text
Framed as a Daily Mix card: subtle concentric orbit rings behind the motif,
gentle dawn-to-night gradient bar at the bottom edge (no text), slightly brighter
and more dynamic than the matching genre cover.
```

## Playlists (curated)

See `catalog.json` `playlists` keys. Album-* and test/empty lists use
`playlists/default.webp` only.

## Regeneration

```sh
# Replace placeholders after Nano Banana export (keep filenames):
# collection_defaults/{pack}/{genres|mixes|playlists}/{slug}.webp
```

Placeholder generator (dev): `scripts/generate-collection-default-placeholders.py`
(optional; tree already generated).
