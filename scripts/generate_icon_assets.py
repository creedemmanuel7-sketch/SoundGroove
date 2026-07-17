"""Generate Android launcher / brand assets from the SoundGroove waveform ring icon."""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs/icon-concepts/soundgroove-icon-v2-waveform-ring.png"
RES = ROOT / "app/src/main/res"

FOREGROUND_SIZES = {
    "drawable-mdpi": 108,
    "drawable-hdpi": 162,
    "drawable-xhdpi": 216,
    "drawable-xxhdpi": 324,
    "drawable-xxxhdpi": 432,
}

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def resize_square(img: Image.Image, size: int) -> Image.Image:
    return img.resize((size, size), Image.Resampling.LANCZOS)


def main() -> None:
    img = Image.open(SRC).convert("RGBA")

    playstore_path = ROOT / "app/src/main/ic_launcher-playstore.png"
    playstore_path.parent.mkdir(parents=True, exist_ok=True)
    resize_square(img, 512).save(playstore_path, optimize=True)
    print(f"Wrote {playstore_path}")

    for folder, size in FOREGROUND_SIZES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        out_path = out_dir / "ic_launcher_foreground.png"
        resize_square(img, size).save(out_path, optimize=True)
        print(f"Wrote {out_path}")

    for folder, size in MIPMAP_SIZES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        resized = resize_square(img, size)
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            out_path = out_dir / name
            resized.save(out_path, optimize=True)
            print(f"Wrote {out_path}")

    brand_dir = RES / "drawable-nodpi"
    brand_dir.mkdir(parents=True, exist_ok=True)
    brand_path = brand_dir / "ic_brand_waveform.png"
    resize_square(img, 256).save(brand_path, optimize=True)
    print(f"Wrote {brand_path}")


if __name__ == "__main__":
    main()
