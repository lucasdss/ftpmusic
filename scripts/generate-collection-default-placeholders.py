#!/usr/bin/env python3
"""Generate style-tinted placeholder WebPs for collection_defaults packs."""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1] / "compose/app/src/main/assets/collection_defaults"

PACKS = [
    ("anime", "Anime", True, (255, 105, 180), (40, 20, 60)),
    ("pixel48", "Pixel 48", False, (0, 220, 120), (20, 20, 20)),
    ("futuristic", "Futuristic", False, (0, 220, 255), (10, 20, 40)),
    ("vintage", "Vintage", False, (200, 140, 60), (50, 30, 15)),
    ("watercolor", "Watercolor", False, (120, 180, 220), (30, 40, 50)),
    ("bauhaus", "Bauhaus", False, (220, 40, 40), (20, 20, 20)),
    ("clay", "Clay", False, (240, 180, 140), (60, 40, 30)),
    ("risograph", "Risograph", False, (255, 60, 120), (30, 10, 40)),
    ("noir", "Noir", False, (200, 200, 200), (10, 10, 10)),
]


def main() -> None:
    catalog = json.loads((ROOT / "catalog.json").read_text())
    genre_slugs = set(catalog["genres"].values()) | {"default"}
    mix_slugs = set(catalog["mixes"].values()) | {"default"}
    playlist_slugs = set(catalog["playlists"].values()) | {"default"}
    count = 0
    for pack_id, _, _, fg, bg in PACKS:
        size = 48 if pack_id == "pixel48" else 512
        for kind, slugs, fname in [
            ("genres", genre_slugs, lambda s: f"{s}.webp"),
            ("mixes", mix_slugs, lambda s: f"{s}_mix.webp" if s != "default" else "default.webp"),
            ("playlists", playlist_slugs, lambda s: f"{s}.webp"),
        ]:
            outdir = ROOT / pack_id / kind
            outdir.mkdir(parents=True, exist_ok=True)
            for slug in sorted(slugs):
                img = make_image(size, fg, bg, slug.replace("-", " "), pack_id)
                img.save(outdir / fname(slug), "WEBP", quality=80, method=4)
                count += 1
    print(f"Wrote {count} webp files under {ROOT}")


def make_image(size: int, fg, bg, label: str, pack_id: str) -> Image.Image:
    if pack_id == "pixel48":
        img = Image.new("RGB", (48, 48), bg)
        d = ImageDraw.Draw(img)
        d.rectangle([4, 4, 43, 43], outline=fg, width=2)
        d.rectangle([12, 12, 35, 35], fill=fg)
        return img.resize((384, 384), Image.NEAREST)
    img = Image.new("RGB", (size, size), bg)
    d = ImageDraw.Draw(img)
    if pack_id == "bauhaus":
        d.rectangle([40, 40, size // 2, size // 2], fill=fg)
        d.ellipse([size // 3, size // 3, size - 40, size - 40], outline=fg, width=12)
    elif pack_id == "noir":
        d.rectangle([0, size // 3, size, 2 * size // 3], fill=(30, 30, 30))
        d.polygon([(0, 0), (size, 0), (size, size // 4)], fill=fg)
    elif pack_id == "futuristic":
        for i in range(6):
            y = 80 + i * 140
            d.line([(60, y), (size - 60, y + 40)], fill=fg, width=6)
        d.ellipse(
            [size // 2 - 120, size // 2 - 120, size // 2 + 120, size // 2 + 120],
            outline=fg,
            width=8,
        )
    elif pack_id == "vintage":
        d.ellipse([80, 80, size - 80, size - 80], outline=fg, width=16)
        d.ellipse([size // 2 - 40, size // 2 - 40, size // 2 + 40, size // 2 + 40], fill=fg)
    elif pack_id == "clay":
        d.rounded_rectangle([100, 100, size - 100, size - 100], radius=80, fill=fg)
    elif pack_id == "risograph":
        d.rectangle([60, 60, size - 60, size - 60], outline=fg, width=10)
        d.ellipse([180, 180, size - 180, size - 180], fill=fg)
    else:
        d.ellipse([120, 120, size - 120, size - 180], outline=fg, width=14)
        d.polygon(
            [(size // 2, 160), (size // 2 + 140, size - 200), (size // 2 - 140, size - 200)],
            fill=fg,
        )
    initials = "".join(w[0] for w in label.split()[:2]).upper()[:2] or "?"
    try:
        font = ImageFont.truetype(
            "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
            96 if size > 100 else 14,
        )
    except Exception:
        font = ImageFont.load_default()
    d = ImageDraw.Draw(img)
    bbox = d.textbbox((0, 0), initials, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    d.text(((size - tw) / 2, (size - th) / 2), initials, fill=(255, 255, 255), font=font)
    return img


if __name__ == "__main__":
    main()
