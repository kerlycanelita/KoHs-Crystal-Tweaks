"""Shared helpers for KoHs mod icons: Minecraft textures, the Codex frame, outlines, glint, GIF packing,
contact sheets and a comparison page. Self-contained (Pillow only): copy it into a mod's tools/icons/.

Units: compose everything at 256 px (the static icon, what Mod Menu shows); the GIF frames are the same
pictures reduced to 128 px. Pixel art is scaled by whole numbers with NEAREST; only light (aura, glow,
gradients) is soft.
"""
import glob
import html
import io
import math
import os
import zipfile
from functools import lru_cache

from PIL import Image, ImageChops, ImageDraw, ImageFilter

METAL = (14, 4, 28)
AURA = (190, 70, 255)
MAGENTA = (212, 92, 255)
SILVER = (201, 195, 214)
RIM_LIT = (150, 140, 170)
RIM_DARK = (58, 30, 90)
LIMIT = 250 * 1024  # Modrinth takes 256 KiB; staying under 256,000 bytes too leaves no doubt


# ----------------------------------------------------------------------------- Minecraft art

@lru_cache(maxsize=None)
def _jar():
    jars = [j for j in glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/*/minecraft-clientonly-deobf-*.jar"))
        if not j.endswith("sources.jar")]
    if not jars:
        raise SystemExit("No Minecraft client jar in the Loom cache: build any 26.x mod once first.")
    # Newest version wins (26.2 > 26.1.2 > 1.21.11 by sort of the version folder).
    def key(path):
        v = os.path.basename(os.path.dirname(path))
        return [int(p) if p.isdigit() else 0 for p in v.replace("-", ".").split(".")]
    return zipfile.ZipFile(sorted(jars, key=key)[-1])


@lru_cache(maxsize=None)
def texture(path):
    """'item/totem_of_undying', 'block/bookshelf', 'particle/sga_a' or a full assets/... path. RGBA.
    Animated textures (strips) come whole: crop the frame you need."""
    if not path.startswith("assets/"):
        path = "assets/minecraft/textures/" + path + ".png"
    return Image.open(io.BytesIO(_jar().read(path))).convert("RGBA")


def big(img, scale):
    """Pixel art blown up by a whole number, every texel a crisp square."""
    return img.resize((img.width * scale, img.height * scale), Image.NEAREST)


def tint(img, rgb):
    """The picture's brightness and alpha in one colour (glyphs, particles)."""
    grey = img.convert("L")
    coloured = Image.merge("RGB", [grey.point(lambda v, c=c: v * c // 255) for c in rgb])
    out = coloured.convert("RGBA")
    out.putalpha(img.getchannel("A"))
    return out


def pixel_art(rows, colours, cell):
    """A picture from strings: one character per cell, '.' or any key not in colours is empty."""
    img = Image.new("RGBA", (len(rows[0]) * cell, len(rows) * cell), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key in colours:
                d.rectangle((x * cell, y * cell, x * cell + cell - 1, y * cell + cell - 1), fill=colours[key] + (255,))
    return img


def outlined(sprite, width=5, metal=METAL, aura=AURA, aura_blur=6, aura_strength=0.75):
    """Black metal round the solid pixels and a soft purple aura behind (never a white sticker rim)."""
    pad = width + aura_blur * 3
    canvas = Image.new("RGBA", (sprite.width + pad * 2, sprite.height + pad * 2), (0, 0, 0, 0))
    canvas.alpha_composite(sprite, (pad, pad))
    body = canvas.getchannel("A").point(lambda v: 255 if v > 90 else 0)
    out = Image.new("RGBA", canvas.size, aura + (0,))
    glow = body.filter(ImageFilter.MaxFilter(width * 2 + 1)).filter(ImageFilter.GaussianBlur(aura_blur))
    out.putalpha(glow.point(lambda v: int(v * aura_strength)))
    edge = Image.new("RGBA", canvas.size, metal + (0,))
    edge.putalpha(body.filter(ImageFilter.MaxFilter(width * 2 + 1)))
    out.alpha_composite(edge)
    out.alpha_composite(canvas)
    return out


def glint(sprite, t, strength=0.6, colour=(255, 120, 255), wash=0.0):
    """The game's enchantment glint over the sprite's own pixels, scrolled by t (0..1 is one seamless loop).
    `wash` first tints the whole sprite towards the glint colour, the purple an enchanted item has at rest."""
    if wash > 0.0:
        washed = Image.blend(sprite.convert("RGB"), Image.new("RGB", sprite.size, colour), wash).convert("RGBA")
        washed.putalpha(sprite.getchannel("A"))
        sprite = washed
    sheet = tint(texture("misc/enchanted_glint_item"), colour).convert("RGB")
    tile = sheet.resize((sprite.width, sprite.width), Image.NEAREST)
    shift = int(round(t * tile.width))
    rolled = ImageChops.offset(tile, shift, shift // 2)
    if rolled.height != sprite.height:
        rolled = rolled.resize(sprite.size, Image.NEAREST)
    lit = ImageChops.add(sprite.convert("RGB"), rolled.point(lambda v: int(v * strength)))
    out = lit.convert("RGBA")
    out.putalpha(sprite.getchannel("A"))
    return out


# ----------------------------------------------------------------------------- the Codex frame

def octagon(size, cut, inset=0.0):
    a, b = inset, size - 1 - inset
    c = cut + inset * 0.414
    return [(a + c, a), (b - c, a), (b, a + c), (b, b - c), (b - c, b), (a + c, b), (a, b - c), (a, a + c)]


def octagon_mask(size, cut, supersample=4):
    """The cut-corner silhouette, antialiased (the GIF thresholds it at half)."""
    s = supersample
    m = Image.new("L", (size * s, size * s), 0)
    ImageDraw.Draw(m).polygon([(x * s, y * s) for x, y in octagon(size, cut)], fill=255)
    return m.resize((size, size), Image.LANCZOS)


def radial(size, cx, cy, radius, inner, outer):
    """A radial gradient from inner (centre) to outer colour, opaque."""
    img = Image.new("RGB", (size, size), outer)
    d = ImageDraw.Draw(img)
    steps = 48
    for k in range(steps, 0, -1):
        r = radius * k / steps
        f = 1.0 - k / steps
        col = tuple(int(o + (i - o) * f) for i, o in zip(inner, outer))
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=col)
    return img.convert("RGBA")


def codex_plate(size=256, inner=(92, 34, 150), outer=(16, 6, 30), centre=(128, 124)):
    """The deep violet plate behind everything (square: `cut_out` gives it the Codex corners)."""
    return radial(size, centre[0], centre[1], size * 0.62, inner, outer)


def codex_rim(size=256, cut=44, width=4):
    """The rim: an outer black-metal band and a lit inner line, on transparent."""
    s = 4
    layer = Image.new("RGBA", (size * s, size * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    outer = [(x * s, y * s) for x, y in octagon(size, cut, width / 2)]
    d.line(outer + [outer[0]], fill=METAL + (255,), width=width * s, joint="curve")
    inner = [(x * s, y * s) for x, y in octagon(size, cut, width + 1.5)]
    d.line(inner + [inner[0]], fill=RIM_LIT + (255,), width=2 * s, joint="curve")
    deep = [(x * s, y * s) for x, y in octagon(size, cut, width + 4)]
    d.line(deep + [deep[0]], fill=RIM_DARK + (255,), width=2 * s, joint="curve")
    return layer.resize((size, size), Image.LANCZOS)


def perimeter_points(size, cut, inset):
    pts = octagon(size, cut, inset)
    return pts + [pts[0]]


def vein(size, cut, t, inset=6.5, length=0.16, colour=MAGENTA, width=4, pulses=2):
    """Pulses of light running round the rim (the Codex's perimeter vein); t 0..1 is one lap."""
    pts = perimeter_points(size, cut, inset)
    segs = []
    total = 0.0
    for a, b in zip(pts, pts[1:]):
        lenseg = math.dist(a, b)
        segs.append((a, b, total, lenseg))
        total += lenseg

    def at(u):
        u = (u % 1.0) * total
        for a, b, start, lenseg in segs:
            if u <= start + lenseg:
                k = (u - start) / lenseg
                return a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k
        return pts[-1]

    s = 4
    layer = Image.new("RGBA", (size * s, size * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for p in range(pulses):
        head = t + p / pulses
        n = 18
        for i in range(n):
            u0 = head - length * i / n
            u1 = head - length * (i + 1) / n
            fade = (1.0 - i / n) ** 1.6
            col = tuple(int(c + (255 - c) * 0.6 * fade) for c in colour) + (int(255 * fade),)
            x0, y0 = at(u0)
            x1, y1 = at(u1)
            d.line((x0 * s, y0 * s, x1 * s, y1 * s), fill=col, width=int(width * s * (0.5 + 0.5 * fade)))
    return layer.resize((size, size), Image.LANCZOS).filter(ImageFilter.GaussianBlur(0.6))


def glow(size, cx, cy, radius, colour, strength):
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=colour + (int(255 * strength),))
    return layer.filter(ImageFilter.GaussianBlur(radius * 0.45))


def place(canvas, sprite, cx, cy, alpha=1.0):
    """Pastes a sprite centred on (cx, cy), optionally see-through (only in the RGBA picture: a GIF cannot fade)."""
    if alpha <= 0.0:
        return
    if alpha < 1.0:
        sprite = sprite.copy()
        sprite.putalpha(sprite.getchannel("A").point(lambda v: int(v * alpha)))
    canvas.alpha_composite(sprite, (int(round(cx - sprite.width / 2)), int(round(cy - sprite.height / 2))))


def cut_out(img, mask):
    """Everything outside the silhouette becomes transparent."""
    out = img.copy()
    out.putalpha(ImageChops.multiply(out.getchannel("A"), mask))
    return out


# ----------------------------------------------------------------------------- output

def save_static(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return os.path.getsize(path)


def save_gif(frames, path, millis=50, fixed=(), colors=255, limit=LIMIT):
    """One palette for every frame (index 255 is see-through), frames after the first only carry what changed
    (disposal 1), so a mostly still picture packs small. Colours of tiny bright details go in `fixed` or the
    big areas eat them. Retries with fewer colours until the file is under Modrinth's 256 KiB."""
    size = frames[0].size
    while True:
        strip = Image.new("RGB", (size[0], size[1] * len(frames)))
        for i, f in enumerate(frames):
            strip.paste(f.convert("RGB"), (0, size[1] * i))
        fixed = list(fixed)
        count = max(16, colors - len(fixed))
        found = strip.quantize(colors=count, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        palette = Image.new("P", (1, 1))
        flat = found.getpalette()[:3 * count] + [c for rgb in fixed for c in rgb]
        flat += [0] * (3 * 256 - len(flat))
        palette.putpalette(flat)
        drawn = []
        previous = None
        for f in frames:
            mapped = f.convert("RGB").quantize(palette=palette, dither=Image.Dither.NONE)
            mapped.paste(255, mask=f.getchannel("A").point(lambda v: 255 if v < 128 else 0))
            if previous is not None:
                same = Image.frombytes("L", mapped.size, bytes(
                    255 if a == b else 0 for a, b in zip(mapped.tobytes(), previous.tobytes())))
                delta = mapped.copy()
                delta.paste(255, mask=same)
                drawn.append(delta)
            else:
                drawn.append(mapped)
            previous = mapped
        os.makedirs(os.path.dirname(path), exist_ok=True)
        drawn[0].save(path, save_all=True, append_images=drawn[1:], duration=millis, loop=0, transparency=255,
                      disposal=1, optimize=False)
        bytes_ = os.path.getsize(path)
        if bytes_ <= limit or colors <= 48:
            return bytes_
        colors -= 32


def sheet(frames, path, cols=8, bg=(20, 14, 28)):
    w, h = frames[0].size
    rows = (len(frames) + cols - 1) // cols
    out = Image.new("RGB", (w * cols, h * rows), bg)
    for i, f in enumerate(frames):
        out.paste(f, ((i % cols) * w, (i // cols) * h), f)
    out.save(path)


def reduce(img, size=128):
    """256 -> 128 for the GIF: an exact 2x box keeps texels crisp and light smooth."""
    if img.width == size:
        return img
    return img.resize((size, size), Image.BOX)


def comparison_page(path, title, candidates, embed=False):
    """An HTML page with every candidate: GIF at 128 and 64, static at 256, 64 and 32, on Modrinth's dark and
    light backgrounds. candidates = [(name, note, gif_path, png_path)], paths relative to the page; `embed`
    puts the images inside the page (heavy: the app's browser pane refuses pages of several MiB)."""
    import base64

    def inline(rel, mime):
        with open(os.path.join(os.path.dirname(path), rel), "rb") as f:
            return "data:%s;base64,%s" % (mime, base64.b64encode(f.read()).decode("ascii"))

    cards = []
    for name, note, gif, png in candidates:
        if embed:
            gif, png = inline(gif, "image/gif"), inline(png, "image/png")
        cards.append(f"""
<section>
  <h2>{html.escape(name)}</h2>
  <p>{html.escape(note)}</p>
  <div class="row dark"><img src="{gif}" width="128"><img src="{gif}" width="64"><img src="{png}" width="256"><img src="{png}" width="64"><img src="{png}" width="32"></div>
  <div class="row light"><img src="{gif}" width="128"><img src="{gif}" width="64"><img src="{png}" width="64"><img src="{png}" width="32"></div>
</section>""")
    page = f"""<!doctype html><html><head><meta charset="utf-8"><title>{html.escape(title)}</title>
<style>
body{{background:#0d0a12;color:#f4edff;font:15px system-ui,sans-serif;margin:24px}}
h1{{font-size:20px;letter-spacing:.3em;text-transform:uppercase;color:#d45cff}}
section{{margin:0 0 28px;padding:16px;border:1px solid #3a3543;border-radius:4px;background:#130b1c}}
h2{{margin:0 0 4px}} p{{margin:0 0 12px;color:#b4a8c8}}
.row{{display:flex;gap:18px;align-items:center;padding:14px;margin-top:8px;border-radius:4px}}
.dark{{background:#16181c}} .light{{background:#ffffff}}
img{{image-rendering:auto}}
</style></head><body><h1>{html.escape(title)}</h1>{''.join(cards)}</body></html>"""
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(page)


def lineup(rows, path_gif, path_png, millis=50, gap=10, bg=(22, 24, 28)):
    """All candidates side by side to choose from: an animated strip (every GIF playing at once, labelled) and
    a still sheet with each static at 256, 64 and 32 px on Modrinth's dark and light backgrounds.
    rows = [(label, frames_128, static_256)]."""
    from PIL import ImageFont
    font = ImageFont.load_default()
    n = len(rows)
    w = 128 * n + gap * (n + 1)
    h = 128 + gap * 2 + 14
    frames = []
    for i in range(len(rows[0][1])):
        f = Image.new("RGBA", (w, h), bg + (255,))
        d = ImageDraw.Draw(f)
        for k, (label, gif_frames, _) in enumerate(rows):
            x = gap + k * (128 + gap)
            f.alpha_composite(gif_frames[i % len(gif_frames)], (x, gap))
            d.text((x + 2, gap + 130), label, fill=(244, 237, 255), font=font)
        frames.append(f)
    save_gif(frames, path_gif, millis, limit=1 << 30)
    cell = 256 + 64 + 32 + gap * 4
    still = Image.new("RGB", (cell * n + gap, (256 + gap * 2) * 2), bg)
    for k, (label, _, static) in enumerate(rows):
        for row, back in enumerate((bg, (255, 255, 255))):
            y = row * (256 + gap * 2)
            x = k * cell + gap
            still.paste(back, (x - gap // 2, y, x + cell - gap // 2, y + 256 + gap * 2))
            still.paste(static, (x, y + gap), static)
            still.paste(static.resize((64, 64), Image.LANCZOS), (x + 256 + gap, y + gap), static.resize((64, 64), Image.LANCZOS))
            small = static.resize((32, 32), Image.LANCZOS)
            still.paste(small, (x + 256 + 64 + gap * 2, y + gap), small)
            ImageDraw.Draw(still).text((x + 256 + gap, y + gap + 80), label, fill=(120, 110, 140) if row else (244, 237, 255), font=font)
    still.save(path_png)
