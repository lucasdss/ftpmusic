#!/usr/bin/env python3
"""Generate collection default cover WebPs via local mflux or Motile LiteLLM.

Assembles rich multi-section prompts from scripts/collection_defaults/prompts.json
and writes into compose/app/src/main/assets/collection_defaults/{pack}/{kind}/.

Backends:
  mflux   — local Apple Silicon (default)
  litellm — OpenAI-compatible /v1/images/generations (e.g. gpt-image-2.5-flare)
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "compose/app/src/main/assets/collection_defaults"
PROMPTS_PATH = ROOT / "scripts/collection_defaults/prompts.json"
CATALOG_PATH = ASSETS / "catalog.json"
PROGRESS_PATH = ROOT / "scripts/collection_defaults/.progress"

DEFAULT_LITELLM_BASE = "http://127.0.0.1:4000/v1"
DEFAULT_LITELLM_IMAGE_MODEL = "gpt-image-2.5-flare"

KIND_GENRES = "genres"
KIND_MIXES = "mixes"
KIND_PLAYLISTS = "playlists"


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def stable_seed(pack: str, kind: str, slug: str) -> int:
    digest = hashlib.sha256(f"{pack}:{kind}:{slug}".encode()).hexdigest()
    return int(digest[:8], 16) % 2_147_483_647


def resolve_subject(prompts: dict, kind: str, slug: str) -> dict:
    if slug == "default":
        return prompts["defaults"][kind]
    if kind == KIND_GENRES:
        return prompts["genres"][slug]
    if kind == KIND_MIXES:
        entry = prompts["mixes"][slug]
        if "inherits_genre" in entry:
            base = dict(prompts["genres"][entry["inherits_genre"]])
            base["display_name"] = f"{base['display_name']} Mix"
            return base
        return entry
    if kind == KIND_PLAYLISTS:
        return prompts["playlists"][slug]
    raise KeyError(f"unknown kind {kind}")


def pack_style_block(pack: dict) -> str:
    return (
        f"Style technique: {pack['technique']}. "
        f"Form language: {pack['line_and_form']}. "
        f"Palette: {pack['palette']}. "
        f"Lighting: {pack['lighting']}. "
        f"Texture: {pack['texture']}. "
        f"Mood: {pack['mood']}. "
        f"Do: {pack['do']}."
    )


def assemble_prompt(prompts: dict, pack_id: str, kind: str, slug: str) -> tuple[str, str]:
    """Front-load motif + style — Flux schnell dilutes long trailing context."""
    pack = prompts["packs"][pack_id]
    card = resolve_subject(prompts, kind, slug)
    palette = ", ".join(card.get("palette_keys") or [])
    parts = [
        # Critical constraints first (repeated later).
        "Square music-app cover art. Absolutely no text, letters, numbers, calligraphy, "
        "kanji, logos, watermarks, faces, or brand marks.",
        # Subject first so the motif is not buried.
        f"Main subject: {card['motif']}.",
        f"Include: {card['props']}.",
        f"Atmosphere: {card['atmosphere']}. Energy: {card['energy']}.",
        f"Color keys: {palette}.",
        f"Keep recognizable across styles: {card['must_keep_across_packs']}.",
        pack_style_block(pack),
        prompts["composition"],
        "FTP Music Android collection tile; original decorative illustration, not a real album photo.",
        prompts["base_constraints"],
    ]
    if kind == KIND_MIXES:
        parts.append(prompts["daily_mix_framing"])
    if kind == KIND_PLAYLISTS and slug != "default":
        parts.append(
            f'Playlist mood inspired by the name "{card["display_name"]}" '
            "(interpret abstractly only; never paint that name)."
        )
    if card.get("notes"):
        parts.append(f"Extra notes: {card['notes']}.")
    neg = prompts["negatives"]
    extra = pack.get("avoid_extra")
    if extra:
        neg = f"{neg}, {extra}"
    parts.append(
        f"Avoid: {neg}, calligraphy, kanji, hangul, arabic script, any written language."
    )
    return " ".join(parts), neg


def catalog_targets(catalog: dict) -> list[tuple[str, str, str]]:
    """Return list of (kind, slug, filename)."""
    targets: list[tuple[str, str, str]] = []
    genre_slugs = sorted(set(catalog["genres"].values()) | {"default"})
    mix_slugs = sorted(set(catalog["mixes"].values()) | {"default"})
    playlist_slugs = sorted(set(catalog["playlists"].values()) | {"default"})
    for slug in genre_slugs:
        targets.append((KIND_GENRES, slug, f"{slug}.webp"))
    for slug in mix_slugs:
        name = "default.webp" if slug == "default" else f"{slug}_mix.webp"
        targets.append((KIND_MIXES, slug, name))
    for slug in playlist_slugs:
        targets.append((KIND_PLAYLISTS, slug, f"{slug}.webp"))
    return targets


def out_path(pack: str, kind: str, filename: str) -> Path:
    return ASSETS / pack / kind / filename


def find_mflux() -> str:
    path = shutil.which("mflux-generate")
    if not path:
        raise SystemExit(
            "mflux-generate not found. Install with: uv tool install -p 3.12 --upgrade mflux"
        )
    return path


# Ungated community conversion — official black-forest-labs/FLUX.1-schnell is gated.
# Prefer q4 (~9.6GB). Needs ~12GB free disk for first download.
DEFAULT_MODEL = "mflux-community/flux-1-schnell-mflux-q4"
DEFAULT_BASE_MODEL = "schnell"


def try_load_flux(model: str, base_model: str):
    """Load Flux once in-process (much faster for batch). Falls back to None."""
    try:
        from mflux.models.common.config.model_config import ModelConfig
        from mflux.models.flux.variants.txt2img.flux import Flux1
    except ImportError:
        return None
    cfg = ModelConfig.from_name(base_model)
    print(f"Loading Flux in-process: {model} (base={base_model})", flush=True)
    return Flux1(model_config=cfg, model_path=model)


def run_mflux_inprocess(
    flux,
    prompt: str,
    seed: int,
    png_out: Path,
    width: int,
    height: int,
    steps: int,
) -> None:
    image = flux.generate_image(
        seed=seed,
        prompt=prompt,
        num_inference_steps=steps,
        height=height,
        width=width,
    )
    image.save(str(png_out), overwrite=True)


def run_mflux_cli(
    mflux: str,
    prompt: str,
    seed: int,
    png_out: Path,
    width: int,
    height: int,
    steps: int,
    low_ram: bool,
    model: str,
    base_model: str,
) -> None:
    # Negatives are baked into the prompt ("Avoid: …"); Flux.1 has no negative branch.
    cmd = [
        mflux,
        "--model",
        model,
        "--base-model",
        base_model,
        "--prompt",
        prompt,
        "--seed",
        str(seed),
        "--width",
        str(width),
        "--height",
        str(height),
        "--steps",
        str(steps),
        "--output",
        str(png_out),
        "--no-exif",
    ]
    if low_ram:
        cmd.append("--low-ram")
    subprocess.run(cmd, check=True)


def postprocess_to_webp(png_path: Path, webp_path: Path, pack_id: str, size: int) -> None:
    img = Image.open(png_path).convert("RGB")
    if pack_id == "pixel48":
        tiny = img.resize((48, 48), Image.Resampling.BILINEAR)
        # Reduce colors for chunky look, then NN upscale for display asset.
        tiny = tiny.quantize(colors=16, method=Image.Quantize.MEDIANCUT).convert("RGB")
        img = tiny.resize((size, size), Image.Resampling.NEAREST)
    else:
        if img.size != (size, size):
            img = img.resize((size, size), Image.Resampling.LANCZOS)
    webp_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(webp_path, "WEBP", quality=92, method=6)


def run_litellm_image(
    prompt: str,
    png_out: Path,
    *,
    api_key: str,
    base_url: str,
    model: str,
    quality: str,
    api_size: str = "1024x1024",
    retries: int = 4,
) -> None:
    """Call LiteLLM OpenAI-compatible images API via curl (LAN-privacy safe)."""
    url = base_url.rstrip("/") + "/images/generations"
    body = {
        "model": model,
        "prompt": prompt,
        "size": api_size,
        "n": 1,
        "quality": quality,
    }
    last_err = ""
    for attempt in range(1, retries + 1):
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as tmp:
            json.dump(body, tmp)
            body_path = Path(tmp.name)
        try:
            cmd = [
                "curl",
                "-sS",
                "-m",
                "180",
                "-o",
                "-",
                "-w",
                "\n%{http_code}",
                "-X",
                "POST",
                "-H",
                f"Authorization: Bearer {api_key}",
                "-H",
                "Content-Type: application/json",
                "-H",
                "Accept: application/json",
                "--data-binary",
                f"@{body_path}",
                url,
            ]
            out = subprocess.check_output(cmd, text=True)
        except subprocess.CalledProcessError as exc:
            last_err = (exc.output or "") + (exc.stderr or "")
            time.sleep(min(2**attempt, 30))
            continue
        finally:
            body_path.unlink(missing_ok=True)
        if "\n" not in out:
            last_err = out
            time.sleep(min(2**attempt, 30))
            continue
        raw, _, code_s = out.rpartition("\n")
        try:
            code = int(code_s.strip())
        except ValueError:
            last_err = out
            continue
        if code in (429, 500, 502, 503, 504):
            last_err = f"HTTP {code}: {raw[:300]}"
            time.sleep(min(2**attempt, 30))
            continue
        if code != 200:
            raise RuntimeError(f"LiteLLM images HTTP {code}: {raw[:500]}")
        data = json.loads(raw)
        items = data.get("data") or []
        if not items:
            raise RuntimeError(f"LiteLLM images empty data: {raw[:300]}")
        item = items[0]
        if item.get("b64_json"):
            png_out.write_bytes(base64.b64decode(item["b64_json"]))
            return
        if item.get("url"):
            # Rare path: download URL with curl
            subprocess.run(
                ["curl", "-sS", "-m", "120", "-o", str(png_out), item["url"]],
                check=True,
            )
            return
        raise RuntimeError(f"LiteLLM images missing b64_json/url: {list(item.keys())}")
    raise RuntimeError(f"LiteLLM images failed after {retries} tries: {last_err[:500]}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--backend",
        choices=["mflux", "litellm"],
        default="mflux",
        help="Image generator backend",
    )
    parser.add_argument("--pack", action="append", help="Limit to pack id (repeatable)")
    parser.add_argument(
        "--kind",
        action="append",
        choices=[KIND_GENRES, KIND_MIXES, KIND_PLAYLISTS],
        help="Limit to kind (repeatable)",
    )
    parser.add_argument("--limit", type=int, default=0, help="Stop after N generations")
    parser.add_argument("--force", action="store_true", help="Overwrite existing WebPs")
    parser.add_argument("--dry-run", action="store_true", help="Print prompts only")
    parser.add_argument("--width", type=int, default=384)
    parser.add_argument("--height", type=int, default=384)
    parser.add_argument("--steps", type=int, default=4)
    parser.add_argument("--model", default=DEFAULT_MODEL, help="mflux HF repo or local path")
    parser.add_argument("--base-model", default=DEFAULT_BASE_MODEL)
    parser.add_argument("--low-ram", action="store_true", default=True)
    parser.add_argument("--no-low-ram", action="store_true")
    parser.add_argument(
        "--quality",
        choices=["low", "medium", "high", "auto"],
        default="low",
        help="LiteLLM/OpenAI image quality (ignored by mflux)",
    )
    parser.add_argument(
        "--litellm-model",
        default=os.environ.get("LITELLM_IMAGE_MODEL", DEFAULT_LITELLM_IMAGE_MODEL),
    )
    parser.add_argument(
        "--litellm-base-url",
        default=os.environ.get("LITELLM_BASE_URL", DEFAULT_LITELLM_BASE),
    )
    args = parser.parse_args()
    low_ram = not args.no_low_ram

    prompts = load_json(PROMPTS_PATH)
    catalog = load_json(CATALOG_PATH)
    packs = args.pack or list(prompts["packs"].keys())
    kinds = set(args.kind) if args.kind else {KIND_GENRES, KIND_MIXES, KIND_PLAYLISTS}
    targets = [(k, s, f) for (k, s, f) in catalog_targets(catalog) if k in kinds]

    # Validate prompt coverage early.
    for kind, slug, _ in targets:
        try:
            resolve_subject(prompts, kind, slug)
        except KeyError as exc:
            raise SystemExit(f"Missing prompt card for {kind}/{slug}: {exc}") from exc

    def load_progress() -> set[str]:
        if not PROGRESS_PATH.exists():
            return set()
        return {
            line.strip()
            for line in PROGRESS_PATH.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.startswith("#")
        }

    def mark_progress(key: str, completed: set[str]) -> None:
        completed.add(key)
        PROGRESS_PATH.parent.mkdir(parents=True, exist_ok=True)
        PROGRESS_PATH.write_text(
            "# pack/kind/filename completed by generate-collection-default-covers.py\n"
            + "\n".join(sorted(completed))
            + "\n",
            encoding="utf-8",
        )

    completed = load_progress()
    litellm_key = os.environ.get("LITELLM_API_KEY", "").strip()
    mflux_cli = None
    flux = None
    if not args.dry_run and args.backend == "litellm":
        if not litellm_key:
            raise SystemExit("LITELLM_API_KEY required for --backend litellm")
        print(
            f"LiteLLM backend model={args.litellm_model} quality={args.quality} "
            f"base={args.litellm_base_url}",
            flush=True,
        )
    elif not args.dry_run:
        flux = try_load_flux(args.model, args.base_model)
        if flux is None:
            mflux_cli = find_mflux()
            print("mflux not importable; using CLI per image (slower)", flush=True)

    done = 0
    skipped = 0
    written: list[Path] = []
    with tempfile.TemporaryDirectory(prefix="ftp-covers-") as tmp:
        tmp_dir = Path(tmp)
        for pack_id in packs:
            if pack_id not in prompts["packs"]:
                raise SystemExit(f"Unknown pack: {pack_id}")
            for kind, slug, filename in targets:
                key = f"{pack_id}/{kind}/{filename}"
                dest = out_path(pack_id, kind, filename)
                if key in completed and not args.force and not args.dry_run:
                    skipped += 1
                    continue
                prompt, _negatives = assemble_prompt(prompts, pack_id, kind, slug)
                seed = stable_seed(pack_id, kind, slug)
                print(f"== {key} seed={seed}", flush=True)
                if args.dry_run:
                    print(prompt)
                    print("---")
                    done += 1
                else:
                    png = tmp_dir / f"{pack_id}_{kind}_{slug}.png"
                    try:
                        if args.backend == "litellm":
                            run_litellm_image(
                                prompt,
                                png,
                                api_key=litellm_key,
                                base_url=args.litellm_base_url,
                                model=args.litellm_model,
                                quality=args.quality,
                            )
                        elif flux is not None:
                            run_mflux_inprocess(
                                flux,
                                prompt=prompt,
                                seed=seed,
                                png_out=png,
                                width=args.width,
                                height=args.height,
                                steps=args.steps,
                            )
                        else:
                            run_mflux_cli(
                                mflux=mflux_cli,
                                prompt=prompt,
                                seed=seed,
                                png_out=png,
                                width=args.width,
                                height=args.height,
                                steps=args.steps,
                                low_ram=low_ram,
                                model=args.model,
                                base_model=args.base_model,
                            )
                        postprocess_to_webp(png, dest, pack_id, args.width)
                        mark_progress(key, completed)
                        written.append(dest.resolve())
                        print(f"wrote {dest.resolve()}", flush=True)
                        done += 1
                    except Exception as exc:  # noqa: BLE001 — continue batch
                        print(f"ERROR {key}: {exc}", flush=True)
                    finally:
                        png.unlink(missing_ok=True)
                if args.limit and done >= args.limit:
                    print(f"limit {args.limit} reached ({skipped} skipped)")
                    if written:
                        print("\n=== GENERATED ABSOLUTE PATHS ===", flush=True)
                        for p in written:
                            print(p, flush=True)
                        print("=== END PATHS ===\n", flush=True)
                    return 0
    print(f"done={done} skipped_existing={skipped}")
    if written:
        print("\n=== GENERATED ABSOLUTE PATHS ===", flush=True)
        for p in written:
            print(p, flush=True)
        print("=== END PATHS ===\n", flush=True)
        print(f"Pack root: {(ASSETS / packs[0]).resolve()}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
