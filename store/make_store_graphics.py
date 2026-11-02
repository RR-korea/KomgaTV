"""Google Play 스토어 그래픽 만들기 — 대표 그래픽(1024x500), 앱 아이콘(512), TV 배너(1280x720).

    python store/make_store_graphics.py <NotoSans-Bold.ttf> <NotoSans-Regular.ttf>

대표 그래픽은 왼쪽에 표시와 글자, 오른쪽에 두 쪽 보기 스크린샷(데모 서버의 공개 만화)을 둔다.
출력: store/graphics/feature_graphic_1024x500.png, app_icon_512.png, tv_banner_1280x720.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "docs" / "branding"))
from make_banner import BG_BOTTOM, BG_TOP, BLUE, INK, SS, banner, gradient, mark, text  # noqa: E402

FW, FH = 1024, 500


def rounded(img: Image.Image, radius: int) -> Image.Image:
    """모서리를 둥글게 자른 RGBA"""
    mask = Image.new("L", (img.width * SS, img.height * SS), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, mask.width - 1, mask.height - 1), radius=radius * SS, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask.resize(img.size, Image.LANCZOS))
    return out


def feature_graphic(bold: str, regular: str, shot: Path) -> Image.Image:
    out = gradient(FW, FH, BG_TOP, BG_BOTTOM).convert("RGBA")
    # 오른쪽: TV 화면처럼 둥근 테두리 + 그림자를 준 스크린샷
    sw = 520
    sh = round(sw * 9 / 16)
    screen = rounded(Image.open(shot).convert("RGB").resize((sw, sh), Image.LANCZOS), 14)
    x, y = FW - sw - 40, (FH - sh) // 2 - 10
    shadow = Image.new("RGBA", out.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((x + 6, y + 14, x + sw + 6, y + sh + 14), radius=18, fill=(0, 0, 0, 90))
    out.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(14)))
    bezel = Image.new("RGBA", (sw + 16, sh + 16), (0, 0, 0, 0))
    ImageDraw.Draw(bezel).rounded_rectangle((0, 0, sw + 15, sh + 15), radius=20, fill=(32, 33, 36, 255))
    out.alpha_composite(bezel, (x - 8, y - 8))
    out.alpha_composite(screen, (x, y))
    # 받침
    d = ImageDraw.Draw(out)
    cx = x + sw // 2
    d.rounded_rectangle((cx - 70, y + sh + 22, cx + 70, y + sh + 30), radius=4, fill=(32, 33, 36, 255))
    # 왼쪽: 표시와 글자
    m = mark(150)
    out.alpha_composite(m, (48, 70))
    title = text(bold, "TV Reader", 64, INK)
    sub = text(regular, "for Komga", 40, BLUE)
    out.alpha_composite(title, (50, 250))
    out.alpha_composite(sub, (52, 250 + title.height + 20))
    return out.convert("RGB")


def main(bold: str, regular: str) -> None:
    dst = HERE / "graphics"
    dst.mkdir(exist_ok=True)
    feature_graphic(bold, regular, HERE / "screenshots" / "en" / "06_two_page.jpg").save(
        dst / "feature_graphic_1024x500.png", optimize=True,
    )
    mark(512).save(dst / "app_icon_512.png", optimize=True)
    banner(bold, regular).save(dst / "tv_banner_1280x720.png", optimize=True)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
