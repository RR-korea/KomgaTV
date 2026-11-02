"""번역 문자열 검사 — 영어(values) 기준으로 각 언어 파일을 비교한다.

  python tools/check_translations.py

빠진·남는 항목, 자리표시자(%1$s, %d …) 불일치, 복수형 수량 누락, 이스케이프 안 된 작은따옴표를 찾는다.
문제가 있으면 종료 코드 1.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLACEHOLDER = re.compile(r"%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxc%]")
# Android가 요구하는 복수형 수량 (CLDR)
REQUIRED_PLURALS = {
    "ko": {"other"}, "ja": {"other"}, "zh-rCN": {"other"}, "zh-rTW": {"other"},
    "de": {"one", "other"}, "it": {"one", "many", "other"}, "fr": {"one", "many", "other"},
    "es": {"one", "many", "other"}, "ru": {"one", "few", "many", "other"},
    "pt-rBR": {"one", "many", "other"}, "pl": {"one", "few", "many", "other"},
    "tr": {"one", "other"}, "in": {"other"}, "vi": {"other"},
}


def load(path: Path):
    raw = path.read_text(encoding="utf-8")
    tree = ET.fromstring(raw)
    strings, plurals = {}, {}
    for el in tree:
        if el.get("translatable") == "false":
            continue
        if el.tag == "string":
            strings[el.get("name")] = "".join(el.itertext())
        elif el.tag == "plurals":
            plurals[el.get("name")] = {i.get("quantity"): "".join(i.itertext()) for i in el}
    return raw, strings, plurals


def placeholders(text: str) -> list[str]:
    return sorted(m.group(0) for m in PLACEHOLDER.finditer(text) if m.group(0) != "%%")


def main() -> int:
    problems = 0
    for en_path in sorted(ROOT.glob("*/**/src/main/res/values/strings.xml")):
        if "build" in en_path.parts:
            continue
        _, en_s, en_p = load(en_path)
        if not en_s and not en_p:
            continue
        res = en_path.parent.parent
        for loc_dir in sorted(res.glob("values-*")):
            loc_file = loc_dir / "strings.xml"
            if not loc_file.exists():
                continue
            locale = loc_dir.name[len("values-"):]
            rel = loc_file.relative_to(ROOT)
            try:
                raw, s, p = load(loc_file)
            except ET.ParseError as e:
                print(f"[XML] {rel}: {e}")
                problems += 1
                continue

            def report(msg):
                nonlocal problems
                problems += 1
                print(f"{rel}: {msg}")

            for name in sorted(set(en_s) - set(s)):
                report(f"빠짐 <string {name}>")
            for name in sorted(set(s) - set(en_s)):
                report(f"남음 <string {name}>")
            for name in sorted(set(en_p) - set(p)):
                report(f"빠짐 <plurals {name}>")
            for name, text in s.items():
                if name in en_s and placeholders(text) != placeholders(en_s[name]):
                    report(f"자리표시자 {name}: {placeholders(text)} ≠ 영어 {placeholders(en_s[name])}")
            need = REQUIRED_PLURALS.get(locale, {"one", "other"})
            for name, items in p.items():
                missing = need - set(items)
                if missing:
                    report(f"복수형 {name}: 수량 {sorted(missing)} 없음")
                want = placeholders(next(iter(en_p[name].values()))) if name in en_p else []
                for q, text in items.items():
                    if placeholders(text) != want:
                        report(f"자리표시자 {name}[{q}]: {placeholders(text)} ≠ {want}")
            # 이스케이프 안 된 작은따옴표 (Android 빌드 오류)
            for m in re.finditer(r"<(string|item)[^>]*>(.*?)</\1>", raw, re.S):
                if re.search(r"(?<!\\)'", m.group(2)):
                    report(f"이스케이프 안 된 ' : {m.group(2)[:40]}")
    print("문제 없음" if problems == 0 else f"문제 {problems}개")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
