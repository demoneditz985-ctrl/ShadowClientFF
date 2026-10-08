#!/usr/bin/env python3
"""Rebuild every Shadow Client icon from one square master image.

    pip install Pillow
    python3 tools/make_icons.py artwork/shadow_logo_master.png [package_dir]

Writes, inside <package_dir>/app/src/main/res:
  mipmap-*/ic_launcher.png            legacy square launcher icon
  mipmap-*/ic_launcher_round.png      legacy round launcher icon
  mipmap-*/ic_launcher_foreground.png adaptive-icon foreground (108dp canvas)
  drawable-nodpi/logo_shadow.png      in-app logo
  drawable-nodpi/logo_shadow_round.png
  drawable-nodpi/ic_overlay_icon.png  floating icon
"""
import os
import sys

from PIL import Image, ImageDraw

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
CYAN = "#A855F7"


def circle_mask(size, inset=0):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse((inset, inset, size - 1 - inset, size - 1 - inset), fill=255)
    return m


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    master_path = sys.argv[1]
    root = sys.argv[2] if len(sys.argv) > 2 else "."
    res = os.path.join(root, "app", "src", "main", "res")

    src = Image.open(master_path).convert("RGBA")
    w, h = src.size
    side = min(w, h)
    square = src.crop(((w - side) // 2, (h - side) // 2,
                       (w - side) // 2 + side, (h - side) // 2 + side)).resize((1024, 1024), Image.LANCZOS)

    for name, scale in DENSITIES.items():
        out = os.path.join(res, "mipmap-" + name)
        os.makedirs(out, exist_ok=True)

        n = int(48 * scale)
        square.resize((n, n), Image.LANCZOS).save(os.path.join(out, "ic_launcher.png"))

        rnd = square.resize((n, n), Image.LANCZOS)
        rnd.putalpha(circle_mask(n, max(1, int(n * 0.02))))
        rnd.save(os.path.join(out, "ic_launcher_round.png"))

        fg = int(108 * scale)
        canvas = Image.new("RGBA", (fg, fg), (0, 0, 0, 0))
        inner = int(fg * 0.67)          # keeps the artwork inside round/squircle masks
        art = square.resize((inner, inner), Image.LANCZOS)
        canvas.paste(art, ((fg - inner) // 2, (fg - inner) // 2), art)
        canvas.save(os.path.join(out, "ic_launcher_foreground.png"))

    nodpi = os.path.join(res, "drawable-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    square.resize((512, 512), Image.LANCZOS).save(os.path.join(nodpi, "logo_shadow.png"))

    big = square.resize((512, 512), Image.LANCZOS)
    big.putalpha(circle_mask(512, 2))
    big.save(os.path.join(nodpi, "logo_shadow_round.png"))

    ov = square.resize((192, 192), Image.LANCZOS)
    ov.putalpha(circle_mask(192, 2))
    ov.save(os.path.join(nodpi, "ic_overlay_icon.png"))

    print("icons written to", res)


if __name__ == "__main__":
    main()
