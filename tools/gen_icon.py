"""The mod's icons, design A "Violet flames" (chosen by the owner, 2026-10-25): the End Crystal turning in
place, its motion blur trailing and fading, the Flames glow style rising behind it in the mod's purple, in
the KoHs Codex frame.

python tools/gen_icon.py
  -> src/main/resources/assets/crystal_tweaks/icon.png   static, 256 x 256 (Mod Menu, fabric.mod.json)
  -> docs/media/icon-animated.gif                         animated, 128 x 128, seamless loop (Modrinth, < 256 KiB)
  -> docs/media/icon-animated-frames.png                  every frame on one sheet, to review

The drawing lives in tools/icons/candidates.py with the other two candidates (B real fire, C preview).
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "icons"))
from candidates import FRAMES, a_violet_flames  # noqa: E402
from iconkit import reduce, save_gif, save_static, sheet  # noqa: E402

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ICON = os.path.join(BASE, "src", "main", "resources", "assets", "crystal_tweaks", "icon.png")
MEDIA = os.path.join(BASE, "docs", "media")
STILL = 0.11  # the static icon's pose, as in the candidates' sheet


def main():
    png = save_static(a_violet_flames(STILL), ICON)
    frames = [reduce(a_violet_flames(i / FRAMES)) for i in range(FRAMES)]
    gif = save_gif(frames, os.path.join(MEDIA, "icon-animated.gif"), millis=50)
    sheet(frames, os.path.join(MEDIA, "icon-animated-frames.png"))
    print(f"icon.png {png} bytes, icon-animated.gif {gif} bytes ({FRAMES} frames, 50 ms)")


if __name__ == "__main__":
    main()
