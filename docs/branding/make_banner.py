"""TV 배너·앱 아이콘 만들기 — Google Play 배너처럼 밝은 바탕에 왼쪽 Komga 로고, 오른쪽 'KomgaTV' (글자 비율 그대로).

입력: Komga 로고(MIT, github.com/gotson/komga komga-webui/src/assets/logo.svg)를 1024px PNG로 그린 것,
      Noto Sans Bold(OFL).
출력: app/src/main/res/drawable-*/tv_banner.png (320x180dp), drawable-*/ic_launcher_foreground.png (108dp, 로고),
      drawable-*/ic_launcher_monochrome.png (테마 아이콘)

    python docs/branding/make_banner.py <logo-1024.png> <NotoSans-Bold.ttf> [표시, 예: ML]
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 1280, 720
# Google Play 타일처럼 흰 바탕에 아주 옅은 회색으로
BG_TOP, BG_BOTTOM = (255, 255, 255), (241, 243, 244)
INK = (60, 64, 67)  # 'Komga' — Google 글자 회색
BLUE = (0, 94, 211)  # 'TV' — Komga 로고의 파랑
BADGE = (232, 64, 52)  # 오른쪽 위 표시 바탕 (빨강)
STRETCH_Y = 1.0  # 글자 비율 그대로 (세로로 늘리면 보기 싫다 — 사용자 의견)
FONT_SIZE = 400
TRACKING = -12  # 자간 (글자 크기 400 기준, 음수면 좁힌다 — 사용자 의견)

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# 적응형 아이콘: 108dp 캔버스, 로고 원은 지름 72dp (런처가 원·둥근 사각으로 잘라도 로고가 온전하게)
ICON_DP, ICON_LOGO_DP = 108, 72


def gradient() -> Image.Image:
    img = Image.new("RGB", (W, H))
    draw = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        draw.line([(0, y), (W, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM)))
    return img


def wordmark(font_path: str, height: int) -> Image.Image:
    """'Komga'(회색) + 'TV'(파랑)을 그린다 (STRETCH_Y가 1이 아니면 세로로 늘린다)"""
    font = ImageFont.truetype(font_path, FONT_SIZE)
    # 한 글자씩 그리며 자간을 [TRACKING]만큼 좁힌다
    letters = [(ch, INK) for ch in "Komga"] + [(ch, BLUE) for ch in "TV"]
    top = min(font.getbbox(ch)[1] for ch, _ in letters)
    bottom = max(font.getbbox(ch)[3] for ch, _ in letters)
    left = font.getbbox(letters[0][0])[0]
    advances = [font.getlength(ch) + TRACKING for ch, _ in letters]
    width = round(sum(advances[:-1]) + font.getbbox(letters[-1][0])[2] - left)
    canvas = Image.new("RGBA", (width, bottom - top), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    x = -left
    for (ch, color), advance in zip(letters, advances):
        draw.text((x, -top), ch, font=font, fill=color)
        x += advance
    stretched = canvas.resize((canvas.width, round(canvas.height * STRETCH_Y)), Image.LANCZOS)
    scale = height / stretched.height
    return stretched.resize((round(stretched.width * scale), height), Image.LANCZOS)


def banner(logo: Image.Image, font_path: str) -> Image.Image:
    out = gradient().convert("RGBA")
    logo_size = 420
    left = 56
    out.alpha_composite(logo.resize((logo_size, logo_size), Image.LANCZOS), (left, (H - logo_size) // 2))

    text_left = left + logo_size + 44
    max_width = W - text_left - 50
    text = wordmark(font_path, 300)
    if text.width > max_width:
        text = text.resize((max_width, round(text.height * max_width / text.width)), Image.LANCZOS)
    out.alpha_composite(text, (text_left, (H - text.height) // 2))
    return out.convert("RGB")


def add_badge(art: Image.Image, font_path: str, label: str) -> Image.Image:
    """오른쪽 위에 둥근 표시 (번역 시험판의 "ML" — 머신러닝)"""
    out = art.convert("RGBA")
    font = ImageFont.truetype(font_path, 120)
    box = font.getbbox(label)
    tw, th = box[2] - box[0], box[3] - box[1]
    pad_x, pad_y, margin = 44, 26, 34
    bw, bh = tw + pad_x * 2, th + pad_y * 2
    x0, y0 = W - margin - bw, margin
    draw = ImageDraw.Draw(out)
    draw.rounded_rectangle((x0, y0, x0 + bw, y0 + bh), radius=bh // 2, fill=BADGE)
    draw.text((x0 + pad_x - box[0], y0 + pad_y - box[1]), label, font=font, fill=(255, 255, 255))
    return out.convert("RGB")


def icon_foreground(logo: Image.Image, px: int) -> Image.Image:
    """108dp 캔버스 가운데 로고 (바탕은 ic_launcher_background — 흰색)"""
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    size = round(px * ICON_LOGO_DP / ICON_DP)
    canvas.alpha_composite(logo.resize((size, size), Image.LANCZOS), ((px - size) // 2, (px - size) // 2))
    return canvas


def icon_monochrome(logo: Image.Image, px: int) -> Image.Image:
    """테마 아이콘(한 색)용 — 로고의 별과 K만 남긴 실루엣 (파란 원은 비운다)"""
    size = round(px * ICON_LOGO_DP / ICON_DP)
    small = logo.resize((size, size), Image.LANCZOS)
    mask = small.getchannel("R").point(lambda r: 255 if r > 150 else 0)
    mask = Image.composite(mask, Image.new("L", small.size, 0), small.getchannel("A"))
    shape = Image.new("RGBA", small.size, (255, 255, 255, 0))
    shape.putalpha(mask)
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    canvas.alpha_composite(shape, ((px - size) // 2, (px - size) // 2))
    return canvas


def main(logo_path: str, font_path: str, badge: str | None = None) -> None:
    logo = Image.open(logo_path).convert("RGBA")
    res = Path(__file__).resolve().parents[2] / "app/src/main/res"
    art = banner(logo, font_path)
    if badge:
        art = add_badge(art, font_path, badge)
    for name, factor in DENSITIES.items():
        size = (round(320 * factor), round(180 * factor))
        art.resize(size, Image.LANCZOS).save(res / f"drawable-{name}/tv_banner.png", optimize=True)
        icon_foreground(logo, round(ICON_DP * factor)).save(
            res / f"drawable-{name}/ic_launcher_foreground.png", optimize=True,
        )
        icon_monochrome(logo, round(ICON_DP * factor)).save(
            res / f"drawable-{name}/ic_launcher_monochrome.png", optimize=True,
        )
    art.save(Path(__file__).with_name("tv_banner_1280.png"))
    icon_foreground(logo, 432).save(Path(__file__).with_name("ic_launcher_432.png"))
    icon_monochrome(logo, 432).save(Path(__file__).with_name("ic_launcher_monochrome_432.png"))


if __name__ == "__main__":
    # 세 번째 인자: 오른쪽 위 표시 (예: ML — translate 브랜치의 번역 시험판)
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
