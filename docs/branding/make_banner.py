"""TV 배너 만들기 — 왼쪽 Komga 로고, 오른쪽 세로로 조금 긴 'KomgaTV' (Google Play 배너처럼).

입력: Komga 로고(MIT, github.com/gotson/komga komga-webui/src/assets/logo.svg)를 1024px PNG로 그린 것,
      Noto Sans Bold(OFL). 출력: app/src/main/res/drawable-*/tv_banner.png (320x180dp, 모든 밀도)

    python docs/branding/make_banner.py <logo-1024.png> <NotoSans-Bold.ttf>
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 1280, 720
NAVY_TOP, NAVY_BOTTOM = (10, 16, 32), (22, 40, 78)
WHITE, BLUE = (255, 255, 255), (77, 163, 255)
STRETCH_Y = 1.6  # 글자를 위아래로 늘려 길쭉하게

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def gradient() -> Image.Image:
    img = Image.new("RGB", (W, H))
    draw = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        draw.line([(0, y), (W, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(NAVY_TOP, NAVY_BOTTOM)))
    return img


def wordmark(font_path: str, height: int) -> Image.Image:
    """'Komga'(흰색) + 'TV'(파랑)을 그린 뒤 세로로 늘린다"""
    font = ImageFont.truetype(font_path, 400)
    parts = [("Komga", WHITE), ("TV", BLUE)]
    widths = [font.getbbox(text)[2] - font.getbbox(text)[0] for text, _ in parts]
    top = min(font.getbbox(text)[1] for text, _ in parts)
    bottom = max(font.getbbox(text)[3] for text, _ in parts)
    gap = 14
    canvas = Image.new("RGBA", (sum(widths) + gap, bottom - top), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    x = 0
    for (text, color), w in zip(parts, widths):
        draw.text((x - font.getbbox(text)[0], -top), text, font=font, fill=color)
        x += w + gap
    stretched = canvas.resize((canvas.width, round(canvas.height * STRETCH_Y)), Image.LANCZOS)
    scale = height / stretched.height
    return stretched.resize((round(stretched.width * scale), height), Image.LANCZOS)


def main(logo_path: str, font_path: str) -> None:
    banner = gradient().convert("RGBA")
    logo_size = 420
    logo = Image.open(logo_path).convert("RGBA").resize((logo_size, logo_size), Image.LANCZOS)
    left = 56
    banner.alpha_composite(logo, (left, (H - logo_size) // 2))

    text_left = left + logo_size + 44
    max_width = W - text_left - 50
    text = wordmark(font_path, 300)
    if text.width > max_width:
        text = text.resize((max_width, round(text.height * max_width / text.width)), Image.LANCZOS)
    banner.alpha_composite(text, (text_left, (H - text.height) // 2))

    out = banner.convert("RGB")
    res = Path(__file__).resolve().parents[2] / "app/src/main/res"
    for name, factor in DENSITIES.items():
        size = (round(320 * factor), round(180 * factor))
        out.resize(size, Image.LANCZOS).save(res / f"drawable-{name}/tv_banner.png", optimize=True)
    out.save(Path(__file__).with_name("tv_banner_1280.png"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
