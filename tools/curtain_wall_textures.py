"""Crystal Tweaks curtain wall: original pixel art, seeded, seamless.

curtain_wall.png: 128 x 64, eight blocks by four of 16 px. Courses of ashlar 8 px tall (a mortar
line and 7 px of stone), stones of varied length staggered course to course, every joint computed
round the 128-px circle so the texture tiles on both axes. Dark purple stone, bevelled, dithered,
with rare cracks, chipped corners, lichen and one carved rune.

merlon_cap.png: 16 x 16, the coping's top: paler stone that faces the sky, a joint per block and a
darker lip along its front.
"""
import random
import sys
from pathlib import Path

from PIL import Image

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent
W, H = 128, 64
COURSE = 8

MORTAR = (14, 10, 20)
MORTAR_DEEP = (10, 7, 15)
STONES = [(28, 22, 38), (33, 26, 45), (38, 30, 51), (43, 34, 57), (48, 39, 63)]
LIGHT = (62, 51, 79)
SHADE = (22, 16, 31)
CRACK = (12, 8, 17)
LICHEN = [(46, 33, 66), (52, 37, 74)]
RUNE = (64, 47, 90)


def clamp(v):
    return max(0, min(255, v))


def tone(c, d):
    return tuple(clamp(x + d) for x in c)


def wall(seed=26_10_2026):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (W, H), MORTAR + (255,))
    px = img.load()
    stones = []
    for course in range(H // COURSE):
        y0 = course * COURSE
        # Joints round the circle: lengths 10..22, the last one closing the loop exactly.
        start = rnd.randrange(0, 16) if course % 2 == 0 else rnd.randrange(16, 32)
        joints = []
        x = start
        while True:
            length = rnd.randint(10, 22)
            if x + length >= start + W - 9:
                length = start + W - x
                joints.append((x, length))
                break
            joints.append((x, length))
            x += length
        for x0, length in joints:
            base = rnd.choice(STONES[:4]) if rnd.random() < 0.85 else STONES[4]
            stones.append((x0, y0, length, base))
    for x0, y0, length, base in stones:
        for dy in range(1, COURSE):
            for dx in range(1, length):
                x = (x0 + dx) % W
                y = y0 + dy
                c = base
                r = rnd.random()
                # Minecraft-like dither: a step up or down the stone's own tone.
                if r < 0.16:
                    c = tone(c, -5)
                elif r < 0.27:
                    c = tone(c, 5)
                if dy == 1 or dx == 1:
                    c = tone(c, 9) if rnd.random() < 0.8 else c
                if dy == COURSE - 1 or dx == length - 1:
                    c = SHADE if rnd.random() < 0.7 else tone(c, -8)
                px[x, y] = c + (255,)
        # Chipped corners: the mortar bites a pixel or two out of some stones.
        if rnd.random() < 0.3:
            corner = rnd.choice([(1, 1), (length - 1, 1), (1, COURSE - 1), (length - 1, COURSE - 1)])
            px[(x0 + corner[0]) % W, y0 + corner[1]] = MORTAR_DEEP + (255,)
        # A crack across a few stones.
        if rnd.random() < 0.12 and length > 12:
            cx = rnd.randint(4, length - 4)
            cy = 1
            while cy < COURSE:
                px[(x0 + cx) % W, y0 + cy] = CRACK + (255,)
                cx += rnd.choice([-1, 0, 0, 1])
                cx = max(2, min(length - 2, cx))
                cy += 1
        # Lichen in a few joints' corners, faint violet.
        if rnd.random() < 0.18:
            lx = rnd.randint(1, length - 2)
            for _ in range(rnd.randint(2, 4)):
                px[(x0 + lx) % W, y0 + COURSE - 1 - rnd.randint(0, 1)] = rnd.choice(LICHEN) + (255,)
                lx = max(1, min(length - 2, lx + rnd.choice([-1, 1])))
    # Mortar varies a little too.
    for y in range(H):
        for x in range(W):
            if px[x, y][:3] == MORTAR and rnd.random() < 0.25:
                px[x, y] = MORTAR_DEEP + (255,)
    # One carved rune on a long stone, the mark that makes the wall its own.
    long_stones = [s for s in stones if s[2] >= 16 and 2 <= s[1] // COURSE <= 5]
    x0, y0, length, base = rnd.choice(long_stones)
    glyph = ["..#..",
             ".#.#.",
             "#.#.#",
             ".###.",
             "..#.."]
    gx = x0 + (length - 5) // 2
    for gy, row in enumerate(glyph):
        for g, ch in enumerate(row):
            if ch == "#":
                px[(gx + g) % W, y0 + 2 + gy] = RUNE + (255,)
    return img


def coping(seed=7):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            c = STONES[3] if y < 13 else STONES[1]
            r = rnd.random()
            if r < 0.18:
                c = tone(c, -6)
            elif r < 0.3:
                c = tone(c, 6)
            if y == 0:
                c = SHADE
            if y in (13,):
                c = LIGHT
            if y >= 14:
                c = SHADE if y == 15 else tone(STONES[1], -4)
            if x == 0:
                c = MORTAR
            px[x, y] = c + (255,)
    return img


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    wall().save(OUT / "curtain_wall.png")
    coping().save(OUT / "merlon_cap.png")
    print("written to", OUT)
