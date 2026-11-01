"""번역 시험용 만화 쪽 두 장을 그린다 — 직접 그린 막대 인물·말풍선, 직접 쓴 대사 (저작물 아님).

  ja_page.png: 일본어 세로쓰기 말풍선 (오른쪽 열부터)
  en_page.png: 영어 대문자 가로쓰기 말풍선

    python docs/translate/make_test_pages.py <YuGothM.ttc> <out_dir>
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 1000, 1500
INK = (20, 20, 20)


def panels(draw: ImageDraw.ImageDraw) -> list[tuple[int, int, int, int]]:
    boxes = [(40, 40, 960, 720), (40, 760, 490, 1460), (530, 760, 960, 1460)]
    for b in boxes:
        draw.rectangle(b, outline=INK, width=6)
    return boxes


def stick_figure(draw: ImageDraw.ImageDraw, x: int, y: int, scale: float = 1.0) -> None:
    r = int(40 * scale)
    draw.ellipse((x - r, y - r, x + r, y + r), outline=INK, width=5)
    body = int(140 * scale)
    draw.line((x, y + r, x, y + r + body), fill=INK, width=5)
    draw.line((x, y + r + 40, x - 60 * scale, y + r + 100 * scale), fill=INK, width=5)
    draw.line((x, y + r + 40, x + 60 * scale, y + r + 100 * scale), fill=INK, width=5)
    draw.line((x, y + r + body, x - 50 * scale, y + r + body + 110 * scale), fill=INK, width=5)
    draw.line((x, y + r + body, x + 50 * scale, y + r + body + 110 * scale), fill=INK, width=5)


def bubble(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int]) -> None:
    draw.ellipse(box, fill="white", outline=INK, width=4)


def vertical_text(draw, font, right_x: int, top_y: int, columns: list[str], size: int) -> None:
    """세로쓰기: 오른쪽 열부터 왼쪽으로, 한 글자씩 아래로"""
    for i, col in enumerate(columns):
        x = right_x - i * int(size * 1.35)
        for j, ch in enumerate(col):
            # 구두점은 칸 오른쪽 위로 (세로쓰기 관례)
            dx, dy = (size * 0.55, -size * 0.45) if ch in "、。" else (0, 0)
            draw.text((x + dx, top_y + j * int(size * 1.08) + dy), ch, font=font, fill=INK)


def horizontal_text(draw, font, center_x: int, top_y: int, lines: list[str], size: int) -> None:
    for i, line in enumerate(lines):
        w = draw.textlength(line, font=font)
        draw.text((center_x - w / 2, top_y + i * int(size * 1.25)), line, font=font, fill=INK)


def ja_page(font_path: str) -> Image.Image:
    img = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(img)
    panels(d)
    size = 34
    f = ImageFont.truetype(font_path, size)
    # 1칸: 두 사람과 말풍선 둘
    stick_figure(d, 300, 400)
    stick_figure(d, 700, 400)
    bubble(d, (560, 70, 900, 330))
    vertical_text(d, f, 770, 105, ["今日は雨だね。", "傘、ある？"], size)
    bubble(d, (90, 70, 400, 330))
    vertical_text(d, f, 270, 105, ["ないよ。", "忘れちゃった。"], size)
    # 2칸
    stick_figure(d, 260, 1150, 0.8)
    bubble(d, (90, 800, 460, 1080))
    vertical_text(d, f, 330, 835, ["じゃあ、", "一緒に帰ろう。"], size)
    # 3칸
    stick_figure(d, 740, 1150, 0.8)
    bubble(d, (570, 800, 930, 1080))
    vertical_text(d, f, 800, 835, ["ありがとう！", "助かるよ。"], size)
    return img


def en_page(font_path: str) -> Image.Image:
    img = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(img)
    panels(d)
    size = 30
    f = ImageFont.truetype(font_path, size)
    stick_figure(d, 300, 420)
    stick_figure(d, 700, 420)
    bubble(d, (520, 70, 930, 300))
    horizontal_text(d, f, 725, 130, ["WHERE ARE", "YOU GOING?"], size)
    bubble(d, (70, 70, 480, 300))
    horizontal_text(d, f, 275, 115, ["TO THE STATION.", "THE TRAIN LEAVES", "AT NINE."], size)
    stick_figure(d, 260, 1170, 0.8)
    bubble(d, (70, 800, 480, 1060))
    horizontal_text(d, f, 275, 860, ["CAN I COME", "WITH YOU?"], size)
    stick_figure(d, 740, 1170, 0.8)
    bubble(d, (550, 800, 940, 1060))
    horizontal_text(d, f, 745, 845, ["SURE! I'LL BUY", "YOU A COFFEE", "ON THE WAY."], size)
    return img


if __name__ == "__main__":
    font, out = sys.argv[1], Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    ja_page(font).save(out / "ja_page.png")
    en_page(font).save(out / "en_page.png")
