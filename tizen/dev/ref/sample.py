"""Kotlin 대조용 표본 만들기 (docs/PLAN.md "Kotlin 대조").

조사 쪽 폴더에서 묶음마다 몇 장씩 골라, 앱이 처리하는 크기(화면에서 차지할 칸)로 줄여 무손실 raw 로 쓴다 —
Kotlin(TizenRefTest.kt)과 TypeScript(test/pageWorkRef.test.ts)가 똑같은 픽셀을 읽게.
raw: 너비·높이(int32 LE) + ARGB int32 LE 픽셀.

    python -I dev/ref/sample.py <조사 쪽 폴더, 예: D:/Project/KomgaTV-gutters-data> <출력 폴더>

묶음(하위 폴더)과 장수는 아래 GROUPS 를 고친다. 보고서에는 줄거리·대사를 쓰지 않는다 (권·쪽 번호만).
"""
import os
import random
import struct
import sys

import numpy as np
from PIL import Image

GROUPS = (("a", 40), ("b", 40), ("c", 40), ("crop", 40), ("kingdom", 80))
SEED = 20261126


def main() -> None:
    root, out = sys.argv[1], sys.argv[2]
    os.makedirs(f"{out}/in", exist_ok=True)
    random.seed(SEED)
    files = []
    for group, count in GROUPS:
        found = []
        for folder, _, names in os.walk(f"{root}/{group}"):
            for name in names:
                if name.lower().endswith((".jpg", ".jpeg", ".png")):
                    found.append(os.path.join(folder, name))
        found.sort()
        files += [(group, path) for path in random.sample(found, min(count, len(found)))]
    index = []
    for i, (group, path) in enumerate(files):
        try:
            image = Image.open(path).convert("RGB")
        except Exception as error:  # 깨진 그림은 건너뛴다
            print("skip", path, error)
            continue
        width, height = image.size
        # 가로로 넓은 그림(두 쪽을 한 장에 스캔)은 화면 전체, 아니면 반 화면 칸에 맞춘다. 키우지는 않는다
        box_w, box_h = (1920, 1080) if width > height else (960, 1080)
        scale = min(box_w / width, box_h / height, 1.0)
        new_w, new_h = max(1, round(width * scale)), max(1, round(height * scale))
        if (new_w, new_h) != (width, height):
            image = image.resize((new_w, new_h), Image.LANCZOS)
        rgb = np.asarray(image, dtype=np.uint32)
        argb = (0xFF000000 | (rgb[:, :, 0] << 16) | (rgb[:, :, 1] << 8) | rgb[:, :, 2]).astype("<u4")
        name = f"{i:03d}_{group}"
        with open(f"{out}/in/{name}.bin", "wb") as f:
            f.write(struct.pack("<ii", new_w, new_h))
            f.write(argb.tobytes())
        index.append(f"{name}\t{new_w}\t{new_h}\t{path}")
    with open(f"{out}/index.tsv", "w", encoding="utf-8") as f:
        f.write("\n".join(index) + "\n")
    print(len(index), "pages")


if __name__ == "__main__":
    main()
