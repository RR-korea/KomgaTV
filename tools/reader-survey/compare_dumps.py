"""dump-old / dump-new 비교 — 새 코드가 옛 코드보다 잉크를 더 잃은 쪽을 찾아 원본|옛|새 그림을 만든다."""
import os, sys, io
import numpy as np
from PIL import Image, ImageFile
ImageFile.LOAD_TRUNCATED_IMAGES = True
sys.stdout.reconfigure(encoding="utf-8")
B = r"D:\Project\KomgaTV-gutters-data"
NEW = os.environ.get("NEWDIR", "dump-new")
OLD = os.environ.get("OLDDIR", "dump-old")
OUT = os.path.join(B, "cmp-" + NEW)
os.makedirs(OUT, exist_ok=True)

def lum(a):
    return (a[..., 0] * 299 + a[..., 1] * 587 + a[..., 2] * 114) // 1000

def ink(l, bg):
    return int((np.abs(l.astype(int) - bg) >= 80).sum())

rows = []
for g in sys.argv[1:] or ["a", "b", "c", "kingdom"]:
    nd = os.path.join(B, NEW, g)
    for dp, _, fs in os.walk(nd):
        for f in fs:
            rel = os.path.relpath(os.path.join(dp, f), nd)
            old = os.path.join(B, OLD, g, rel)
            if not os.path.exists(old):
                continue
            base = os.path.splitext(rel)[0]
            src = next((os.path.join(B, g, base + e) for e in (".jpg", ".jpeg", ".png", ".JPG") if os.path.exists(os.path.join(B, g, base + e))), None)
            if not src:
                continue
            o = np.asarray(Image.open(src).convert("RGB")).astype(np.int32)
            a = np.asarray(Image.open(old).convert("RGB")).astype(np.int32)
            n = np.asarray(Image.open(os.path.join(dp, f)).convert("RGB")).astype(np.int32)
            if np.array_equal(a, n):
                rows.append((g, base, 0.0, 0.0, False))
                continue
            lo = lum(o)
            hist = np.bincount(lo.ravel(), minlength=256)
            bg = int(hist.argmax())
            io_ = ink(lo, bg)
            la = ink(lum(a), bg)
            ln = ink(lum(n), bg)
            lossA = (io_ - la) / max(io_, 1)
            lossN = (io_ - ln) / max(io_, 1)
            rows.append((g, base, lossA, lossN, True))

changed = [r for r in rows if r[4]]
worse = sorted([r for r in changed if r[3] - r[2] > 0.01], key=lambda r: r[2] - r[3])
print(f"pages {len(rows)}, changed {len(changed)}, new loses >1% more ink: {len(worse)}")
for g, base, la, ln, _ in worse[:40]:
    print(f"  {g}/{base}: old loss {la:.3f} -> new loss {ln:.3f}")
    src = next(os.path.join(B, g, base + e) for e in (".jpg", ".jpeg", ".png", ".JPG") if os.path.exists(os.path.join(B, g, base + e)))
    ims = [Image.open(p).convert("RGB") for p in (src, os.path.join(B, OLD, g, base + ".png"), os.path.join(B, NEW, g, base + ".png"))]
    h = 800
    ims = [im.resize((round(im.width * h / im.height), h)) for im in ims]
    c = Image.new("RGB", (sum(i.width for i in ims) + 20, h), "red")
    x = 0
    for im in ims:
        c.paste(im, (x, 0))
        x += im.width + 10
    c.save(os.path.join(OUT, (g + "_" + base).replace(os.sep, "_") + ".jpg"), quality=85)
