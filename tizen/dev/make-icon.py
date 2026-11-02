# Android "TV Reader for Komga" 배너(docs/branding/tv_banner_1280.png)를 Tizen 앱 아이콘(512x423)으로.
# 같은 흰→옅은 회색 바탕을 새로 그리고, 배너에서 바탕을 뺀 그림·글자만 알파로 꺼내 가운데에 둔다.
import sys
from pathlib import Path
from PIL import Image, ImageDraw

here = Path(__file__).parent
# 입력: Android docs/branding/tv_banner_1280.png 경로, 출력: static/icon.png
banner = Image.open(sys.argv[1]).convert("RGB")
BG_TOP, BG_BOTTOM = (255, 255, 255), (241, 243, 244)
W0, H0 = banner.size


def bg_at(y, h):
    t = y / (h - 1)
    return tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM))


# 바탕과 다른 픽셀만: 알파 = 바탕과의 차이(부드럽게), 색은 바탕을 빼고 되살린다
src = banner.load()
layer = Image.new("RGBA", (W0, H0), (0, 0, 0, 0))
dst = layer.load()
for y in range(H0):
    bg = bg_at(y, H0)
    for x in range(W0):
        p = src[x, y]
        diff = max(abs(p[i] - bg[i]) for i in range(3))
        if diff <= 3:
            continue
        a = min(1.0, diff / 60.0)
        # p = a*c + (1-a)*bg → c
        c = tuple(max(0, min(255, round((p[i] - (1 - a) * bg[i]) / a))) for i in range(3))
        dst[x, y] = c + (round(a * 255),)
content = layer.crop(layer.getbbox())

W, H = 512, 423
out = Image.new("RGBA", (W, H))
d = ImageDraw.Draw(out)
for y in range(H):
    d.line([(0, y), (W, y)], fill=bg_at(y, H) + (255,))
scale = (W * 0.90) / content.width
small = content.resize((round(content.width * scale), round(content.height * scale)), Image.LANCZOS)
out.alpha_composite(small, ((W - small.width) // 2, (H - small.height) // 2))
dest = Path(sys.argv[2])
out.convert("RGB").save(dest, optimize=True)
print("saved", dest, small.size)
