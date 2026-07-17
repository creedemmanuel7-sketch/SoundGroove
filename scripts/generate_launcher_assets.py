"""Generate Android launcher mipmaps + website favicon from brand winner PNG."""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
WINNER = ROOT / "docs" / "brand-exploration" / "01-flat-g-vinyl-violet.png"
RES = ROOT / "app" / "src" / "main" / "res"
PUBLIC = ROOT / "website" / "public"

# Legacy launcher sizes (px)
MIPMAP = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def round_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    # Full opaque — OEM applies adaptive mask; legacy round uses same art
    from PIL import ImageDraw

    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    return mask


def main() -> None:
    src = Image.open(WINNER).convert("RGBA")
    # Square crop to content
    w, h = src.size
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    src = src.crop((left, top, left + side, top + side))

    playstore = src.resize((512, 512), Image.Resampling.LANCZOS)
    playstore.save(RES / "drawable" / "ic_launcher_playstore.png", optimize=True)
    playstore.save(RES / "drawable" / "ic_launcher_foreground_asset.png", optimize=True)
    playstore.save(RES / "drawable-nodpi" / "ic_launcher_foreground_asset.png", optimize=True)

    # Website icons
    PUBLIC.mkdir(parents=True, exist_ok=True)
    src.resize((512, 512), Image.Resampling.LANCZOS).save(PUBLIC / "icon.png", optimize=True)
    src.resize((180, 180), Image.Resampling.LANCZOS).save(PUBLIC / "apple-touch-icon.png", optimize=True)
    src.resize((32, 32), Image.Resampling.LANCZOS).save(PUBLIC / "favicon-32.png", optimize=True)

    for folder, size in MIPMAP.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        icon = src.resize((size, size), Image.Resampling.LANCZOS)
        icon.save(out_dir / "ic_launcher.png", optimize=True)
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_icon.paste(icon, (0, 0))
        round_icon.putalpha(round_mask(size))
        # Composite on transparent then flatten to opaque black circle for legacy
        bg = Image.new("RGBA", (size, size), (0, 0, 0, 255))
        bg.paste(icon, (0, 0))
        circ = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        circ.paste(bg, mask=round_mask(size))
        circ.save(out_dir / "ic_launcher_round.png", optimize=True)

    # Remove density PNG foregrounds so vector drawable/ic_launcher_foreground.xml wins
    for density in (
        "drawable-mdpi",
        "drawable-hdpi",
        "drawable-xhdpi",
        "drawable-xxhdpi",
        "drawable-xxxhdpi",
    ):
        png = RES / density / "ic_launcher_foreground.png"
        if png.exists():
            png.unlink()

    print("Launcher assets generated from", WINNER.name)


if __name__ == "__main__":
    main()
