"""TV 배너·앱 아이콘 만들기 — "TV Reader for Komga".

직접 그린 표시(파란 둥근 사각형 안에 흰 TV 화면, 그 안에 펼친 책)와 글자 "TV Reader" / "for Komga".
Komga 로고는 쓰지 않는다 (다른 프로젝트의 상표로 보이지 않게).

    python docs/branding/make_banner.py <NotoSans-Bold.ttf> <NotoSans-Regular.ttf> [오른쪽 위 표시, 예: ML]

출력: app/src/main/res/drawable-*/tv_banner.png (320x180dp), drawable-*/ic_launcher_foreground.png,
      drawable-*/ic_launcher_monochrome.png (108dp), docs/branding/*.png (미리보기·스토어용 512px 아이콘)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 1280, 720
BG_TOP, BG_BOTTOM = (255, 255, 255), (241, 243, 244)
INK = (60, 64, 67)
BLUE = (0, 94, 211)
BLUE_DARK = (0, 66, 160)
BADGE = (232, 64, 52)

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
# 적응형 아이콘: 108dp 캔버스, 런처가 잘라도 온전한 안쪽에 그림을 둔다
ICON_DP, ICON_SAFE_DP = 108, 66
SS = 4  # 4배로 그린 뒤 줄여 가장자리를 부드럽게


def gradient(w: int, h: int, top, bottom) -> Image.Image:
    img = Image.new("RGB", (w, h))
    draw = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(h - 1, 1)
        draw.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return img


def tv_book(size: int, color, screen_fill=None) -> Image.Image:
    """TV(둥근 화면 + 받침) 안에 펼친 책 — [size] 정사각 캔버스, 투명 바탕.
    [screen_fill]이 있으면 화면 안을 그 색으로 칠하고 책의 글줄·접힘을 그 색으로 비운다(없으면 투명)."""
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    stroke = round(s * 0.055)
    left, top, right, bottom = s * 0.10, s * 0.14, s * 0.90, s * 0.74
    if screen_fill:
        d.rounded_rectangle((left, top, right, bottom), radius=s * 0.09, fill=screen_fill)
    d.rounded_rectangle((left, top, right, bottom), radius=s * 0.09, outline=color, width=stroke)
    # 받침
    d.line((s * 0.50, bottom, s * 0.50, s * 0.83), fill=color, width=stroke)
    d.rounded_rectangle((s * 0.32, s * 0.81, s * 0.68, s * 0.81 + stroke * 1.2), radius=stroke * 0.6, fill=color)
    # 펼친 책
    cx, cy = s * 0.50, s * 0.445
    bw, bh = s * 0.25, s * 0.30
    for side in (-1, 1):
        d.polygon(
            [(cx, cy - bh * 0.40), (cx + side * bw, cy - bh * 0.55), (cx + side * bw, cy + bh * 0.42), (cx, cy + bh * 0.55)],
            fill=color,
        )
    gap = screen_fill or (0, 0, 0, 0)
    line_w = round(s * 0.02)
    for side in (-1, 1):
        for i in range(3):
            y0 = cy - bh * 0.20 + i * bh * 0.24
            d.line(
                (cx + side * bw * 0.20, y0 + bh * 0.04, cx + side * bw * 0.78, y0 - bh * 0.04),
                fill=gap, width=line_w,
            )
    d.line((cx, cy - bh * 0.40, cx, cy + bh * 0.55), fill=gap, width=round(s * 0.016))
    return img.resize((size, size), Image.LANCZOS)


def mark(size: int) -> Image.Image:
    """파란 둥근 사각형 + 흰 TV·책 (배너·스토어 아이콘용)"""
    s = size * SS
    grad = gradient(s, s, BLUE, BLUE_DARK).convert("RGBA")
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, s - 1, s - 1), radius=s * 0.22, fill=255)
    base = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    base.paste(grad, (0, 0), mask)
    base = base.resize((size, size), Image.LANCZOS)
    inner = tv_book(round(size * 0.80), (255, 255, 255, 255), screen_fill=BLUE + (255,))
    base.alpha_composite(inner, ((size - inner.width) // 2, (size - inner.height) // 2 + round(size * 0.03)))
    return base


def text(font_path: str, txt: str, px: int, color) -> Image.Image:
    font = ImageFont.truetype(font_path, px)
    l, t, r, b = font.getbbox(txt)
    img = Image.new("RGBA", (r - l, b - t), (0, 0, 0, 0))
    ImageDraw.Draw(img).text((-l, -t), txt, font=font, fill=color)
    return img


def fit(img: Image.Image, max_w: int) -> Image.Image:
    return img if img.width <= max_w else img.resize((max_w, round(img.height * max_w / img.width)), Image.LANCZOS)


def banner(bold: str, regular: str) -> Image.Image:
    out = gradient(W, H, BG_TOP, BG_BOTTOM).convert("RGBA")
    m = mark(400)
    left = 72
    out.alpha_composite(m, (left, (H - m.height) // 2))
    x = left + m.width + 60
    max_w = W - x - 56
    title = fit(text(bold, "TV Reader", 210, INK), max_w)
    sub = fit(text(regular, "for Komga", 112, BLUE), max_w)
    gap = 48
    y = (H - (title.height + gap + sub.height)) // 2
    out.alpha_composite(title, (x, y))
    out.alpha_composite(sub, (x + 4, y + title.height + gap))
    return out.convert("RGB")


def add_badge(art: Image.Image, font_path: str, label: str) -> Image.Image:
    """오른쪽 위 둥근 표시 (시험판 구별용)"""
    out = art.convert("RGBA")
    font = ImageFont.truetype(font_path, 120)
    l, t, r, b = font.getbbox(label)
    pad_x, pad_y, margin = 44, 26, 34
    bw, bh = (r - l) + pad_x * 2, (b - t) + pad_y * 2
    x0, y0 = W - margin - bw, margin
    d = ImageDraw.Draw(out)
    d.rounded_rectangle((x0, y0, x0 + bw, y0 + bh), radius=bh // 2, fill=BADGE)
    d.text((x0 + pad_x - l, y0 + pad_y - t), label, font=font, fill=(255, 255, 255))
    return out.convert("RGB")


def icon_layer(px: int, screen_fill) -> Image.Image:
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    size = round(px * ICON_SAFE_DP / ICON_DP)
    glyph = tv_book(size, (255, 255, 255, 255), screen_fill=screen_fill)
    canvas.alpha_composite(glyph, ((px - size) // 2, (px - size) // 2))
    return canvas


def main(bold: str, regular: str, badge: str | None = None) -> None:
    here = Path(__file__).resolve().parent
    res = here.parents[1] / "app/src/main/res"
    art = banner(bold, regular)
    if badge:
        art = add_badge(art, bold, badge)
    for name, factor in DENSITIES.items():
        art.resize((round(320 * factor), round(180 * factor)), Image.LANCZOS).save(
            res / f"drawable-{name}/tv_banner.png", optimize=True,
        )
        px = round(ICON_DP * factor)
        # 앞면: 흰 TV·책 (바탕 ic_launcher_background가 파랑), 테마 아이콘: 실루엣
        icon_layer(px, BLUE + (255,)).save(res / f"drawable-{name}/ic_launcher_foreground.png", optimize=True)
        icon_layer(px, None).save(res / f"drawable-{name}/ic_launcher_monochrome.png", optimize=True)
    art.save(here / "tv_banner_1280.png")
    mark(512).save(here / "store_icon_512.png")
    for old in ("ic_launcher_432.png", "ic_launcher_monochrome_432.png"):
        (here / old).unlink(missing_ok=True)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
