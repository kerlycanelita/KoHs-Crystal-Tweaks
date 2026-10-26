"""Crystal Tweaks icon candidates: the End Crystal turning in place (no float), its own motion blur
trailing behind it and fading, fire glowing behind it, in the KoHs Codex frame.

A - violet flames: the Flames glow style in the mod's purple, rising behind the crystal and past its top.
B - real fire: the game's fire burning on bedrock under the purple crystal, as on the End's pillars.
C - the preview: the crystal before the mod's castle wall, over violet flames on obsidian.

Run: py -3 tools/icons/candidates.py  (writes docs/media/icon-candidates/)
"""
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(__file__))
import iconkit as kit  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "docs", "media", "icon-candidates")
SIZE = 256
CUT = 44
FRAMES = 48
SS = 2  # the crystal is drawn at twice the size, then reduced: smooth edges, texels still visible

# ----------------------------------------------------------------------------- the crystal

TEX = kit.texture("entity/end_crystal/end_crystal")
TEXEL = TEX.width // 64  # 26.x ships it at 128 x 64: two pixels a texel


def region(u, v):
    """One 8 x 8 face of the model's 64 x 32 layout."""
    return TEX.crop((u * TEXEL, v * TEXEL, (u + 8) * TEXEL, (v + 8) * TEXEL))


def tinted(img, rgb, keep=0.0):
    """Light of the face in one colour, its alpha kept; `keep` leaves some of its own colours."""
    lum = kit.tint(img, rgb)
    if keep <= 0.0:
        return lum
    mixed = Image.blend(lum.convert("RGB"), img.convert("RGB"), keep).convert("RGBA")
    mixed.putalpha(img.getchannel("A"))
    return mixed


# The model's box at texOffs (0, 0) and the core's at (32, 0): top, bottom, then the four sides.
GLASS_FACES = [region(8, 0), region(16, 0), region(0, 8), region(8, 8), region(16, 8), region(24, 8)]
CORE_FACES = [region(40, 0), region(48, 0), region(32, 8), region(40, 8), region(48, 8), region(56, 8)]
def glass(img, rgb):
    """The frame bright, the pane between its edges almost clear, so the core shows through."""
    out = tinted(img, rgb)
    out.putalpha(img.getchannel("A").point(lambda v: v if v > 200 else int(v * 0.35)))
    return out


OUTER = [glass(f, (222, 186, 255)) for f in GLASS_FACES]
INNER = [glass(f, (196, 140, 255)) for f in GLASS_FACES]
CORE = [tinted(f, (178, 86, 255), keep=0.25) for f in CORE_FACES]

# Cube corners and faces (top, bottom, west, north, east, south) as corner index lists:
# p0 is the texture's top-left, p1 its top-right, p3 its bottom-left.
CORNERS = [(x, y, z) for x in (-1, 1) for y in (-1, 1) for z in (-1, 1)]


def idx(x, y, z):
    return CORNERS.index((x, y, z))


FACES = [
    (idx(-1, 1, -1), idx(1, 1, -1), idx(1, 1, 1), idx(-1, 1, 1)),      # top
    (idx(-1, -1, 1), idx(1, -1, 1), idx(1, -1, -1), idx(-1, -1, -1)),  # bottom
    (idx(-1, 1, -1), idx(-1, 1, 1), idx(-1, -1, 1), idx(-1, -1, -1)),  # west
    (idx(1, 1, -1), idx(-1, 1, -1), idx(-1, -1, -1), idx(1, -1, -1)),  # north
    (idx(1, 1, 1), idx(1, 1, -1), idx(1, -1, -1), idx(1, -1, 1)),      # east
    (idx(-1, 1, 1), idx(1, 1, 1), idx(1, -1, 1), idx(-1, -1, 1)),      # south
]


def quat(axis, angle):
    ax, ay, az = axis
    n = math.sqrt(ax * ax + ay * ay + az * az)
    s = math.sin(angle / 2) / n
    return (math.cos(angle / 2), ax * s, ay * s, az * s)


def qmul(a, b):
    aw, ax, ay, az = a
    bw, bx, by, bz = b
    return (aw * bw - ax * bx - ay * by - az * bz, aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx, aw * bz + ax * by - ay * bx + az * bw)


def rotate(q, v):
    w, x, y, z = q
    vx, vy, vz = v
    # v' = q v q*
    tx = 2 * (y * vz - z * vy)
    ty = 2 * (z * vx - x * vz)
    tz = 2 * (x * vy - y * vx)
    return (vx + w * tx + (y * tz - z * ty), vy + w * ty + (z * tx - x * tz), vz + w * tz + (x * ty - y * tx))


TILT = quat((math.sqrt(0.5), 0.0, math.sqrt(0.5)), math.pi / 3)  # the model stands on a corner
VIEW = quat((1.0, 0.0, 0.0), 0.32)  # seen a little from above


def cube_faces(q, scale, cx, cy, unit, textures):
    """The cube's six faces on screen: (depth, facing, quad points, texture)."""
    pts = []
    for corner in CORNERS:
        x, y, z = rotate(VIEW, rotate(q, tuple(c * scale for c in corner)))
        pts.append((cx + x * unit, cy - y * unit, z))
    out = []
    for face, tex in zip(FACES, textures):
        p = [pts[i] for i in face]
        # Facing the viewer when the screen winding is clockwise (y grows downwards).
        (x0, y0, _), (x1, y1, _), (x3, y3, _) = p[0], p[1], p[3]
        cross = (x1 - x0) * (y3 - y0) - (y1 - y0) * (x3 - x0)
        depth = sum(v[2] for v in p) / 4
        out.append((depth, cross > 0, p, tex))
    return out


def draw_face(canvas, quad, tex):
    """A texture laid on a parallelogram (the orthographic view keeps faces affine)."""
    (x0, y0, _), (x1, y1, _), _, (x3, y3, _) = quad
    ux, uy = x1 - x0, y1 - y0
    vx, vy = x3 - x0, y3 - y0
    det = ux * vy - uy * vx
    if abs(det) < 1e-3:
        return
    w, h = tex.size
    # Inverse: screen (x, y) -> texture (u, v).
    a = vy / det * w
    b = -vx / det * w
    d = -uy / det * h
    e = ux / det * h
    c = -(a * x0 + b * y0)
    f = -(d * x0 + e * y0)
    layer = tex.transform(canvas.size, Image.AFFINE, (a, b, c, d, e, f), resample=Image.NEAREST)
    canvas.alpha_composite(layer)


def crystal(turn, size, cx, cy, unit):
    """The three boxes at `turn` radians: outer glass, inner glass, core, back to front."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    spin = quat((0.0, 1.0, 0.0), turn)
    outer_q = qmul(spin, TILT)
    nested = qmul(TILT, spin)
    inner_q = qmul(outer_q, nested)
    core_q = qmul(inner_q, nested)
    outer = cube_faces(outer_q, 1.0, cx, cy, unit, OUTER)
    inner = cube_faces(inner_q, 0.875, cx, cy, unit, INNER)
    core = cube_faces(core_q, 0.765, cx, cy, unit, CORE)
    for faces in (outer, inner):
        for depth, front, quad, tex in sorted(faces, key=lambda f: f[0]):
            if not front:
                draw_face(canvas, quad, tex)
    for depth, front, quad, tex in sorted(core, key=lambda f: f[0]):
        if front:
            draw_face(canvas, quad, tex)
    for faces in (inner, outer):
        for depth, front, quad, tex in sorted(faces, key=lambda f: f[0]):
            if front:
                draw_face(canvas, quad, tex)
    return canvas


def blurred(img, radius, colour):
    """Blurred toward `colour` rather than the black under its transparent pixels: no grey fringe."""
    under = Image.new("RGBA", img.size, colour + (0,))
    under.paste(img, mask=img.getchannel("A").point(lambda v: 255 if v else 0))
    return under.filter(ImageFilter.GaussianBlur(radius))


def spinning_crystal(t, cx, cy, unit, glow_rgb, trail=6, step=0.17):
    """The crystal at loop time t (one full turn per loop, no float), its motion blur behind it: the
    poses it just left, each fainter and softer, and a glow of its own light."""
    size = SIZE * SS
    turn = t * 2 * math.pi
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for k in range(trail, 0, -1):
        ghost = crystal(turn - k * step, size, cx * SS, cy * SS, unit * SS)
        ghost = blurred(ghost, 0.8 + 0.5 * k, (200, 150, 255))
        fade = 0.72 * (1.0 - k / (trail + 1)) ** 1.3
        ghost.putalpha(ghost.getchannel("A").point(lambda v, f=fade: int(v * f)))
        out.alpha_composite(ghost)
    main = crystal(turn, size, cx * SS, cy * SS, unit * SS)
    halo = Image.new("RGBA", main.size, glow_rgb + (0,))
    halo.putalpha(main.getchannel("A").filter(ImageFilter.GaussianBlur(9 * SS)).point(lambda v: int(v * 0.8)))
    out.alpha_composite(halo)
    out.alpha_composite(main)
    return out.resize((SIZE, SIZE), Image.LANCZOS)


# ----------------------------------------------------------------------------- fire

FIRE = kit.texture("block/fire_0").convert("RGBA")
FIRE_FRAMES = FIRE.height // FIRE.width


def fire_frame(index):
    w = FIRE.width
    frame = FIRE.crop((0, (index % FIRE_FRAMES) * w, w, (index % FIRE_FRAMES) * w + w))
    # The texture is opaque black where there is no fire: its light becomes its alpha.
    lum = frame.convert("L")
    frame.putalpha(lum.point(lambda v: 0 if v < 24 else min(255, int(v * 1.6))))
    return frame


def violet(img):
    """Fire through the mod's purple: dark violet at its edge, magenta, then a pale lilac heart."""
    lum = img.convert("L")
    ramp = [(40, 0, 70), (120, 30, 200), (200, 80, 255), (240, 170, 255), (255, 235, 255)]

    def channel(i):
        def f(v):
            x = v / 255 * (len(ramp) - 1)
            k = min(len(ramp) - 2, int(x))
            return int(ramp[k][i] + (ramp[k + 1][i] - ramp[k][i]) * (x - k))
        return lum.point(f)
    out = Image.merge("RGB", [channel(0), channel(1), channel(2)]).convert("RGBA")
    out.putalpha(img.getchannel("A"))
    return out


def flames(t, recolour, base_y, tongues, glow_rgb, glow_strength):
    """Tongues of the game's fire, (dx, whole-number scale, frame offset) each, and their glow."""
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for dx, scale, offset in tongues:
        frame = fire_frame(int(t * FIRE_FRAMES) + offset)
        if recolour:
            frame = recolour(frame)
        sprite = kit.big(frame, scale)
        layer.alpha_composite(sprite, (SIZE // 2 + int(dx) - sprite.width // 2, int(base_y) - sprite.height))
    glow = kit.tint(layer, glow_rgb).filter(ImageFilter.GaussianBlur(14))
    glow.putalpha(glow.getchannel("A").point(lambda v: int(v * glow_strength)))
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    out.alpha_composite(glow)
    out.alpha_composite(layer)
    return out


# Tongues of fire, (dx from the middle, root rise, height, half width, lean): a bed under the crystal,
# or the Flames style's crown, rooted behind it and climbing past its top.
BED = [(0, 0, 176, 27, 0), (-36, 4, 146, 23, 12), (36, 4, 146, 23, -12), (-66, 10, 112, 20, 18),
           (66, 10, 112, 20, -18), (-92, 18, 78, 16, 20), (92, 18, 78, 16, -20)]
CROWN = [(0, 0, 178, 30, 0), (-40, 6, 152, 24, 14), (40, 6, 152, 24, -14), (-74, 16, 116, 20, 22),
         (74, 16, 116, 20, -22), (-100, 30, 80, 15, 24), (100, 30, 80, 15, -24)]
FIRE_RAMP = [(0, 0, 0), (70, 10, 140), (150, 40, 235), (215, 95, 255), (240, 160, 255), (250, 205, 255)]


def flame_light(t, cx, base_y, tongues=BED, scale=1.0, ramp=FIRE_RAMP, glow=0.9):
    """The mod's Flames glow style, as light to add: tongues rising from under the crystal and past its
    top, swaying, licking and flickering, hot in the middle, fading to nothing at the tip. Whole numbers
    of flickers a loop keep it seamless."""
    s = SS
    acc = Image.new("L", (SIZE * s, SIZE * s), 0)
    for i, (dx, rise, height, width, lean) in enumerate(tongues):
        flicker = 0.88 + 0.12 * math.sin(2 * math.pi * t * (2 + i % 3) + i * 2.3)
        h = height * scale * flicker
        line = []
        for step in range(17):
            f = step / 16.0
            lick = (math.sin(2 * math.pi * t * (1 + i % 2) * 2 - f * 5.0 + i * 1.9) * 8
                    + math.sin(2 * math.pi * t * 3 - f * 9.0 + i) * 3) * scale * f
            x = cx + (dx + lean * f) * scale + lick
            y = base_y - rise * scale - h * f
            w = width * scale * (1.0 - f) ** 0.8 * min(1.0, 0.45 + 2.5 * f)
            light = min(1.0, 4.0 * f + 0.2) * (1.0 - f) ** 0.55
            line.append((x * s, y * s, w * s, light))
        one = Image.new("L", acc.size, 0)
        d = ImageDraw.Draw(one)
        for band, k in ((1.0, 0.22), (0.78, 0.4), (0.56, 0.58), (0.36, 0.76), (0.18, 0.9)):
            for (x0, y0, w0, l0), (x1, y1, w1, l1) in zip(line, line[1:]):
                d.polygon([(x0 - w0 * band, y0), (x0 + w0 * band, y0), (x1 + w1 * band, y1), (x1 - w1 * band, y1)],
                          fill=int(255 * k * (l0 + l1) / 2))
        acc = ImageChops.add(acc, one)
    acc = acc.filter(ImageFilter.GaussianBlur(2.0 * s)).resize((SIZE, SIZE), Image.LANCZOS)
    if glow > 0.0:
        acc = ImageChops.add(acc, acc.filter(ImageFilter.GaussianBlur(12)).point(lambda v: int(v * glow)))

    def channel(c):
        def f(v):
            x = v / 255 * (len(ramp) - 1)
            k = min(len(ramp) - 2, int(x))
            return int(ramp[k][c] + (ramp[k + 1][c] - ramp[k][c]) * (x - k))
        return acc.point(f)
    return Image.merge("RGB", [channel(0), channel(1), channel(2)])


def lit(pic, light):
    """Light added onto the opaque picture, as the game adds it."""
    out = ImageChops.add(pic.convert("RGB"), light).convert("RGBA")
    out.putalpha(pic.getchannel("A"))
    return out


def embers(t, colours, base_y, count=14, height=150):
    """Sparks rising from the fire and shrinking as they go (a GIF cannot fade them)."""
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for i in range(count):
        phase = (t * (1 + i % 2) + i * 0.618) % 1.0
        x = SIZE / 2 + math.sin(i * 2.4 + phase * 6.283) * (28 + (i * 37) % 46)
        y = base_y - phase * height
        r = 3.2 * (1.0 - phase) + 0.6
        col = colours[i % len(colours)]
        d.rectangle((x - r, y - r, x + r, y + r), fill=col + (255,))
    return layer


# ----------------------------------------------------------------------------- the frame

MASK = kit.octagon_mask(SIZE, CUT)


def framed(picture, t, vein_colour=kit.MAGENTA):
    out = kit.cut_out(picture, MASK)
    out.alpha_composite(kit.codex_rim(SIZE, CUT))
    out.alpha_composite(kit.vein(SIZE, CUT, t, colour=vein_colour))
    return out


def plate(inner, outer):
    return kit.codex_plate(SIZE, inner=inner, outer=outer, centre=(128, 150))


# ----------------------------------------------------------------------------- the candidates

def a_violet_flames(t):
    pic = plate((96, 30, 150), (14, 4, 26))
    pic = lit(pic, flame_light(t, 128, 170, CROWN))
    pic.alpha_composite(embers(t, [(232, 160, 255), (255, 120, 230), (190, 90, 255)], 196))
    pic.alpha_composite(spinning_crystal(t, 128, 116, 46, (210, 90, 255)))
    return framed(pic, t)


BEDROCK = kit.texture("block/bedrock")


def b_real_fire(t):
    pic = plate((70, 24, 110), (12, 4, 22))
    # The End's pillars: the crystal's fire burns on bedrock.
    floor = kit.big(BEDROCK, 2)
    for x in range(-4, SIZE, floor.width):
        for y in (224, 256):
            pic.alpha_composite(floor, (x, y))
    shade = Image.new("RGBA", (SIZE, SIZE - 224), (10, 4, 20, 110))
    pic.alpha_composite(shade, (0, 224))
    pic.alpha_composite(flames(t, None, 226, [(-50, 6, 11), (50, 6, 23), (0, 8, 0)], (255, 140, 40), 0.9))
    pic.alpha_composite(embers(t, [(255, 210, 120), (255, 150, 60), (255, 240, 200)], 206))
    pic.alpha_composite(spinning_crystal(t, 128, 116, 46, (255, 150, 70)))
    return framed(pic, t, vein_colour=(255, 170, 90))


WALL = Image.open(os.path.join(ROOT, "src", "main", "resources", "crystal_tweaks_fixed", "textures", "curtain_wall.png")).convert("RGBA")
OBSIDIAN = kit.texture("block/obsidian")


def c_preview(t):
    pic = Image.new("RGBA", (SIZE, SIZE), (10, 6, 16, 255))
    # The mod's own wall, 32 px a block, the floor of obsidian under the crystal.
    wall = kit.big(WALL, 2)
    for y in range(-16, SIZE, wall.height):
        for x in range(-16, SIZE, wall.width):
            pic.alpha_composite(wall, (x, y))
    shade = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    ImageDraw.Draw(shade).rectangle((0, 0, SIZE, SIZE), fill=(8, 2, 16, 30))
    pic.alpha_composite(shade)
    floor = kit.big(OBSIDIAN, 2)
    for x in range(-8, SIZE, floor.width):
        pic.alpha_composite(floor, (x, 210))
        pic.alpha_composite(floor, (x, 242))
    light = kit.glow(SIZE, 128, 120, 92, (170, 70, 255), 0.55)
    pic.alpha_composite(light)
    pic = lit(pic, flame_light(t, 128, 212, scale=0.95))
    pic.alpha_composite(embers(t, [(232, 160, 255), (255, 120, 230)], 200, count=10))
    pic.alpha_composite(spinning_crystal(t, 128, 118, 44, (210, 90, 255)))
    vignette = kit.radial(SIZE, 128, 128, SIZE * 0.72, (0, 0, 0), (0, 0, 0))
    vignette.putalpha(kit.radial(SIZE, 128, 128, SIZE * 0.72, (0, 0, 0), (150, 150, 150)).convert("L"))
    pic.alpha_composite(vignette)
    return framed(pic, t)


CANDIDATES = [
    ("A", "violet-flames", "Violet flames: the Flames glow style in the mod's purple", a_violet_flames, 0.11),
    ("B", "real-fire", "Real fire: the game's fire on bedrock under the crystal, as on the End's pillars", b_real_fire, 0.11),
    ("C", "preview", "The preview: the crystal before the mod's castle wall, over violet flames on obsidian", c_preview, 0.11),
]


def main():
    rows = []
    cards = []
    for letter, name, note, draw, still_t in CANDIDATES:
        folder = os.path.join(OUT, f"{letter}-{name}")
        frames256 = [draw(i / FRAMES) for i in range(FRAMES)]
        static = draw(still_t)
        kit.save_static(static, os.path.join(folder, "icon.png"))
        frames128 = [kit.reduce(f) for f in frames256]
        size = kit.save_gif(frames128, os.path.join(folder, "icon-animated.gif"), millis=50)
        kit.sheet(frames128, os.path.join(folder, "frames.png"))
        print(f"{letter}-{name}: gif {size // 1024} KiB")
        rows.append((f"{letter} {name}", frames128, static))
        cards.append((f"{letter} - {name}", note, f"{letter}-{name}/icon-animated.gif", f"{letter}-{name}/icon.png"))
    kit.lineup(rows, os.path.join(OUT, "all-animated.gif"), os.path.join(OUT, "all-static.png"))
    kit.comparison_page(os.path.join(OUT, "index.html"), "Crystal Tweaks icons", cards)


if __name__ == "__main__":
    main()
