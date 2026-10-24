#!/usr/bin/env python3
"""Renders LiteCast's raster brand assets (legacy launcher icons + Android TV banner).

The adaptive icon itself is a vector (res/drawable/lc_ic_launcher_*.xml); this script draws the
same mark with Pillow for the places Android still wants bitmaps. Needs Pillow and a Noto Sans CJK
font (fonts-noto-cjk). Run from the repository root:  python3 tools/make_brand_assets.py
"""
import pathlib
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

RES = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res"
ACCENT = (94, 226, 183, 255)
BG_TOP = (28, 38, 48)
BG_BOTTOM = (11, 13, 16)
FONT_DIR = pathlib.Path("/usr/share/fonts/opentype/noto")


def gradient(size, top, bottom, diagonal=True):
    w, h = size
    img = Image.new("RGBA", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = ((x / max(w - 1, 1)) + (y / max(h - 1, 1))) / 2 if diagonal else y / max(h - 1, 1)
            px[x, y] = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
    return img


def draw_mark(draw, ox, oy, s, color):
    """Draw the mark in a 108-unit design space scaled by s and offset by (ox, oy)."""
    w = 5 * s

    def p(x, y):
        return ox + x * s, oy + y * s

    def cap(x, y):
        cx, cy = p(x, y)
        draw.ellipse([cx - w / 2, cy - w / 2, cx + w / 2, cy + w / 2], fill=color)

    def line(x1, y1, x2, y2):
        draw.line([p(x1, y1), p(x2, y2)], fill=color, width=round(w))
        cap(x1, y1)
        cap(x2, y2)

    def arc(cx, cy, r, start, end):
        x0, y0 = p(cx - r, cy - r)
        x1, y1 = p(cx + r, cy + r)
        draw.arc([x0, y0, x1, y1], start, end, fill=color, width=round(w))

    # frame: left stub, top, right, bottom stub, with rounded corners (r = 5)
    line(33, 46, 33, 40.5)
    for (cx, cy, a0, a1) in ((38, 40.5, 180, 270), (71, 40.5, 270, 360), (71, 67.5, 0, 90)):
        r_out, r_in = 5 + 2.5, 5 - 2.5
        x0, y0 = p(cx - r_out, cy - r_out)
        x1, y1 = p(cx + r_out, cy + r_out)
        draw.pieslice([x0, y0, x1, y1], a0, a1, fill=color)
        xi0, yi0 = p(cx - r_in, cy - r_in)
        xi1, yi1 = p(cx + r_in, cy + r_in)
        draw.pieslice([xi0, yi0, xi1, yi1], a0, a1, fill=(0, 0, 0, 0))
    line(38, 35.5, 71, 35.5)
    line(76, 40.5, 76, 67.5)
    line(71, 72.5, 54, 72.5)
    # waves from the bottom-left corner
    for r in (9, 17):
        arc(33, 72.5, r, 270, 360)
        cap(33, 72.5 - r)
        cap(33 + r, 72.5)
    cx, cy = p(33.5, 72.5)
    rr = 3.5 * s
    draw.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=color)


def mark_layer(size, scale, ox, oy, color=ACCENT, ss=4):
    big = Image.new("RGBA", (size[0] * ss, size[1] * ss), (0, 0, 0, 0))
    draw_mark(ImageDraw.Draw(big), ox * ss, oy * ss, scale * ss, color)
    return big.resize(size, Image.LANCZOS)


def legacy_icon(px):
    """Rounded-square launcher icon for Android 6/7 (adaptive icons need 8.0+)."""
    ss = 4
    size = px * ss
    base = gradient((size, size), BG_TOP, BG_BOTTOM)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)
    icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    icon.paste(base, (0, 0), mask)
    # 108-unit artwork; crop the central 72 units like a launcher mask would
    scale = size / 72
    mark = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw_mark(ImageDraw.Draw(mark), -18 * scale, -18 * scale, scale, ACCENT)
    icon.alpha_composite(mark)
    return icon.resize((px, px), Image.LANCZOS)


def font(weight, size):
    path = FONT_DIR / f"NotoSansCJK-{weight}.ttc"
    if not path.exists():
        sys.exit(f"missing font {path} (install fonts-noto-cjk)")
    return ImageFont.truetype(str(path), size, index=2)  # index 2 = Simplified Chinese face


def banner(w, h):
    img = gradient((w, h), BG_TOP, BG_BOTTOM)
    # soft glow behind the mark
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    r = h * 0.55
    gx, gy = w * 0.245, h * 0.5
    gd.ellipse([gx - r, gy - r, gx + r, gy + r], fill=(94, 226, 183, 38))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(h * 0.18)))
    mark_size = int(h * 0.54)
    scale = mark_size / 50  # mark spans ~43x37 units; 50 gives breathing room
    mark = mark_layer((w, h), scale, gx - 54.5 * scale, gy - 54 * scale)
    img.alpha_composite(mark)
    d = ImageDraw.Draw(img)
    title = font("Bold", int(h * 0.30))
    sub = font("Medium", int(h * 0.12))
    tx = int(w * 0.45)
    d.text((tx, int(h * 0.22)), "轻投", font=title, fill=(242, 244, 247, 255))
    d.text((tx + 4, int(h * 0.62)), "LiteCast", font=sub, fill=(169, 176, 188, 255))
    return img


def main():
    for qualifier, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        out = RES / f"mipmap-{qualifier}" / "lc_ic_launcher.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        legacy_icon(px).save(out, optimize=True)
    for qualifier, (w, h) in (("xhdpi", (640, 360)), ("xxhdpi", (960, 540))):
        out = RES / f"drawable-{qualifier}" / "lc_banner.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        banner(w, h).convert("RGB").save(out, optimize=True)
    print("assets written under", RES)


if __name__ == "__main__":
    main()
