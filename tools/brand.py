"""Refract Launcher brand art: a frosted-glass tile holding a minimal prism that splits a beam of
white light into a soft spectrum. Rendered large and downsampled for clean edges.

Usage: python3 -I tools/brand.py <repo root>   (needs Pillow; writes res/ and docs/images/)
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(sys.argv[1])
FONTS = ROOT / "assets" / "fonts"
TOOLS = ROOT / "tools"  # tools/fonts: Inter Display Thin (OFL), used only to draw the wordmark
SPECTRUM = [(255, 92, 120), (255, 170, 80), (255, 230, 110), (90, 230, 160), (70, 190, 255), (150, 120, 255)]


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def gradient(w, h, stops, angle=135):
    """Linear gradient across the canvas at `angle` degrees."""
    img = Image.new("RGB", (w, h))
    px = img.load()
    ax, ay = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    span = abs(ax) * w + abs(ay) * h
    for y in range(h):
        for x in range(w):
            t = ((x - w / 2) * ax + (y - h / 2) * ay) / span + 0.5
            t = min(max(t, 0), 1) * (len(stops) - 1)
            i = min(int(t), len(stops) - 2)
            px[x, y] = lerp(stops[i], stops[i + 1], t - i)
    return img


def background(w, h):
    """Deep night-blue base with blurred colour orbs: what the glass refracts."""
    bg = gradient(w // 4, h // 4, [(10, 12, 30), (24, 20, 64), (8, 34, 52)]).resize((w, h), Image.BICUBIC)
    orbs = Image.new("RGB", (w, h), (0, 0, 0))
    d = ImageDraw.Draw(orbs)
    s = min(w, h)
    for cx, cy, r, col in [(0.22, 0.25, 0.34, (120, 70, 255)), (0.82, 0.78, 0.38, (0, 170, 220)),
                           (0.78, 0.18, 0.22, (255, 80, 150))]:
        d.ellipse([cx * w - r * s, cy * h - r * s, cx * w + r * s, cy * h + r * s], fill=col)
    orbs = orbs.filter(ImageFilter.GaussianBlur(s * 0.16))
    return ImageChops.add(bg, ImageChops.multiply(orbs, Image.new("RGB", (w, h), (150, 150, 150))))


def rounded_mask(size, box, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle(box, radius=radius, fill=255)
    return m


def glass_tile(base, box, radius):
    """Frosted glass: blurred, brightened backdrop inside the tile, a top sheen and a light rim."""
    w, h = base.size
    mask = rounded_mask(base.size, box, radius)
    frost = base.filter(ImageFilter.GaussianBlur(min(w, h) * 0.05))
    frost = Image.blend(frost, Image.new("RGB", base.size, (255, 255, 255)), 0.16)
    out = base.copy()
    # soft drop shadow
    sh = Image.new("L", base.size, 0)
    x0, y0, x1, y1 = box
    off = (y1 - y0) * 0.05
    ImageDraw.Draw(sh).rounded_rectangle([x0, y0 + off, x1, y1 + off], radius=radius, fill=120)
    sh = sh.filter(ImageFilter.GaussianBlur((y1 - y0) * 0.07))
    out = Image.composite(Image.new("RGB", base.size, (0, 0, 0)), out, sh)
    out = Image.composite(frost, out, mask)
    # sheen: brighter at the top-left, fading down
    sheen = gradient(w // 4, h // 4, [(255, 255, 255), (0, 0, 0)], angle=70).resize((w, h)).convert("L")
    sheen = ImageChops.multiply(sheen, mask).point(lambda v: v * 0.22)
    out = Image.composite(Image.new("RGB", base.size, (255, 255, 255)), out, sheen)
    # rim: bright hairline, stronger at the top
    rim = Image.new("L", base.size, 0)
    stroke = max(2, int((x1 - x0) * 0.012))
    ImageDraw.Draw(rim).rounded_rectangle(box, radius=radius, outline=255, width=stroke)
    fade = gradient(w // 4, h // 4, [(255, 255, 255), (90, 90, 90)], angle=90).resize((w, h)).convert("L")
    rim = ImageChops.multiply(rim, fade).point(lambda v: v * 0.8)
    return Image.composite(Image.new("RGB", base.size, (255, 255, 255)), out, rim)


def prism(img, cx, cy, size, beam=False):
    """A minimal rounded glass prism. Light is refracted inside it: a soft spectral caustic runs
    along the right face, a specular highlight along the left. No beam or fan, so it stays a mark."""
    r = size / 2
    tri = [(cx, cy - r * 0.92), (cx - r * 0.98, cy + r * 0.78), (cx + r * 0.98, cy + r * 0.78)]
    lw = max(2, int(size * 0.05))
    W, H = img.size
    inside = Image.new("L", img.size, 0)
    ImageDraw.Draw(inside).polygon(tri, fill=255)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).polygon(tri, fill=(255, 255, 255, 22))
    # spectral caustic: bands parallel to the right face, stepping inward, clipped to the glass
    top, right = tri[0], tri[2]
    ex, ey = right[0] - top[0], right[1] - top[1]
    ln = math.hypot(ex, ey)
    nx, ny = -ey / ln, ex / ln  # inward normal (points left/down into the prism)
    if nx > 0:
        nx, ny = -nx, -ny
    band = Image.new("RGBA", img.size, (0, 0, 0, 0))
    bd = ImageDraw.Draw(band)
    step = size * 0.034
    for i, col in enumerate(SPECTRUM):
        o = lw * 0.9 + i * step
        p0 = (top[0] + nx * o - ex * 0.1, top[1] + ny * o - ey * 0.1)
        p1 = (right[0] + nx * o + ex * 0.1, right[1] + ny * o + ey * 0.1)
        bd.line([p0, p1], fill=col + (230,), width=int(step * 1.25))
    band = band.filter(ImageFilter.GaussianBlur(size * 0.014))
    # fade the caustic towards the top so it reads as light pooling at the base
    fade = Image.new("L", img.size, 0)
    fd = ImageDraw.Draw(fade)
    for y in range(int(tri[0][1]), int(tri[1][1]) + 1, 2):
        t = (y - tri[0][1]) / (tri[1][1] - tri[0][1])
        fd.line([(0, y), (W, y)], fill=int(255 * min(1, 0.15 + t * 1.1)), width=2)
    band.putalpha(ImageChops.multiply(ImageChops.multiply(band.getchannel("A"), inside), fade))
    layer = Image.alpha_composite(layer, band)
    # specular highlight on the left face
    hl = Image.new("RGBA", img.size, (255, 255, 255, 0))  # white, so the blur has no dark fringe
    left = tri[1]
    hd = ImageDraw.Draw(hl)
    hx, hy = (left[0] - top[0]), (left[1] - top[1])
    hd.line([(top[0] + hx * 0.22 + lw * 1.6, top[1] + hy * 0.22 + lw * 0.4),
             (top[0] + hx * 0.62 + lw * 1.6, top[1] + hy * 0.62 + lw * 0.4)], fill=(255, 255, 255, 170), width=int(lw * 0.7))
    hl = hl.filter(ImageFilter.GaussianBlur(lw * 0.5))
    layer = Image.alpha_composite(layer, hl)
    # rounded outline
    d = ImageDraw.Draw(layer)
    d.line(tri + [tri[0], tri[1]], fill=(255, 255, 255, 250), width=lw, joint="curve")
    for vx, vy in tri:
        d.ellipse([vx - lw / 2, vy - lw / 2, vx + lw / 2, vy + lw / 2], fill=(255, 255, 255, 250))
    glow = layer.filter(ImageFilter.GaussianBlur(size * 0.035))
    base = Image.alpha_composite(img.convert("RGBA"), glow)
    base = Image.alpha_composite(base, layer)
    return base.convert("RGB")


def black_tile(size, radius_frac):
    """Pure black rounded tile with a faint glass rim: the stage for the prism."""
    S = size
    img = Image.new("RGB", (S, S), (0, 0, 0))
    # a barely-there top sheen so the black reads as glass, not a hole
    sheen = gradient(S // 4, S // 4, [(34, 34, 40), (0, 0, 0)], angle=90).resize((S, S))
    mask = rounded_mask((S, S), [0, 0, S, S], S * radius_frac)
    img = Image.composite(ImageChops.lighter(img, sheen.point(lambda v: v // 2)), img, mask)
    rim = Image.new("L", (S, S), 0)
    ImageDraw.Draw(rim).rounded_rectangle([1, 1, S - 2, S - 2], radius=S * radius_frac, outline=255, width=max(2, S // 140))
    fade = gradient(S // 4, S // 4, [(150, 150, 150), (30, 30, 30)], angle=90).resize((S, S)).convert("L")
    img = Image.composite(Image.new("RGB", (S, S), (255, 255, 255)), img, ImageChops.multiply(rim, fade))
    return img, mask


def icon(px=512):
    S = px * 4
    img, mask = black_tile(S, 0.22)
    img = prism(img, S * 0.5, S * 0.53, S * 0.56)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out.resize((px, px), Image.LANCZOS)


def mark(px=256):
    """The prism on a black glass tile (used inside the app)."""
    return icon(px)


def banner(w=640, h=360):
    """Black banner: the prism, then a tvOS-style wordmark pairing a heavy and a thin weight."""
    S = 3
    W, H = w * S, h * S
    img = Image.new("RGB", (W, H), (0, 0, 0))
    m = H * 0.3
    bold = ImageFont.truetype(str(FONTS / "InterDisplay-Bold.ttf"), int(H * 0.125))
    thin = ImageFont.truetype(str(TOOLS / "fonts" / "InterDisplay-Thin.ttf"), int(H * 0.125))
    a, b = "Refract", " Launcher"
    wa = bold.getlength(a)
    wb = thin.getlength(b)
    gap = W * 0.04
    total = m + gap + wa + wb
    x0 = (W - total) / 2
    img = prism(img, x0 + m / 2, H * 0.5, m)
    d = ImageDraw.Draw(img)
    base = H * 0.5 + H * 0.045
    tx = x0 + m + gap
    d.text((tx, base), a, font=bold, fill=(255, 255, 255), anchor="ls")
    d.text((tx + wa, base), b, font=thin, fill=(255, 255, 255), anchor="ls")
    return img.resize((w, h), Image.LANCZOS)


if __name__ == "__main__":
    res = ROOT / "res"
    (res / "drawable-xxxhdpi").mkdir(exist_ok=True)
    (res / "drawable-nodpi").mkdir(exist_ok=True)
    icon(192).save(res / "drawable-xxxhdpi" / "ic_launcher.png", optimize=True)
    banner(640, 360).save(res / "drawable-xxxhdpi" / "banner.png", optimize=True)
    mark(256).save(res / "drawable-nodpi" / "refract_mark.png", optimize=True)
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs" / "images"
    out.mkdir(parents=True, exist_ok=True)
    icon(1024).save(out / "refract-icon.png", optimize=True)
    banner(1280, 720).save(out / "refract-banner.png", optimize=True)
    print("done")
