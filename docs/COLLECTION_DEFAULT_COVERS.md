# Collection default covers — local mflux prompt pack

Offline generation only. Assets land in
`compose/app/src/main/assets/collection_defaults/{packId}/…`.

Generator: **mflux** on Apple Silicon using ungated
`mflux-community/flux-1-schnell-mflux-q4` (`--base-model schnell`).
Rich prompts live in [`scripts/collection_defaults/prompts.json`](../scripts/collection_defaults/prompts.json).
Batch driver: [`scripts/generate-collection-default-covers.py`](../scripts/generate-collection-default-covers.py).

## Packs shipped in the APK (5)

| id | Label |
|---|---|
| anime | Anime (default) |
| futuristic | Futuristic |
| vintage | Vintage |
| watercolor | Watercolor |
| noir | Noir |

Archived for later (not in APK): `pixel48`, `bauhaus`, `clay`, `risograph` under
[`scripts/collection_defaults/archived_packs/`](../scripts/collection_defaults/archived_packs/).

## Prompt assembly

Every image gets a multi-section prompt. **Subject motif is front-loaded**
(Flux.1 schnell dilutes long trailing context):

1. Hard no-text / no-calligraphy constraint
2. Subject card first (motif, props, atmosphere, palette, cross-pack lock)
3. Pack style card (technique, form, palette, lighting, texture, mood, do)
4. App composition (centered ~70%, thumbnail-clear, rounded-crop margins)
5. Product context + shared base constraints
6. Kind framing (Daily Mix rings / playlist title-mood)
7. Negatives (`Avoid: …` plus pack `avoid_extra` + scripts)

Slug maps: `assets/collection_defaults/catalog.json`.

## Output

- Size: **384×384** WebP q80 (app tiles; not print resolution)
- `pixel48`: generate → quantize to 16 colors at 48 → nearest-neighbor to 384
- Filenames unchanged: `genres/{slug}.webp`, `mixes/{slug}_mix.webp`, `playlists/{slug}.webp`

## Regeneration

```sh
# One-time tool install (Apple Silicon)
uv tool install -p 3.12 --upgrade mflux

# Preview full assembled prompts
uv run --with Pillow python scripts/generate-collection-default-covers.py \
  --dry-run --pack anime --kind genres --limit 1

# Prefer the mflux uv-tool interpreter so the model stays loaded in-process:
MFLUX_PY="$HOME/.local/share/uv/tools/mflux/bin/python"

# Smoke
"$MFLUX_PY" scripts/generate-collection-default-covers.py \
  --pack anime --kind genres --limit 3 --force

# Full pack tree (resume-safe via scripts/collection_defaults/.progress)
"$MFLUX_PY" scripts/generate-collection-default-covers.py
```

Flags: `--backend mflux|litellm`, `--pack`, `--kind`, `--limit`, `--force`, `--dry-run`,
`--width/--height`, `--steps`, `--quality low|medium|high`, `--model` (mflux),
`--litellm-model`, `--litellm-base-url`.
Resume uses `scripts/collection_defaults/.progress` (gitignored).

### LiteLLM / ChatGPT (Motile)

Requires tunnel + virtual key with `gpt-image-2.5-flare` allowed:

```sh
export LITELLM_API_KEY="$(cat ../mediaserver/mediaserver-automation/scripts/litellm_virtual_key.local)"
export LITELLM_BASE_URL="http://127.0.0.1:4000/v1"

# One pack only (review before continuing):
uv run --with Pillow python scripts/generate-collection-default-covers.py \
  --backend litellm --pack anime --force --quality low
```

Script prints absolute paths of every written WebP at the end.

Placeholder fallback (dev only): `scripts/generate-collection-default-placeholders.py`.
