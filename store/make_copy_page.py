"""store/listing/*.txt → store/listing/copy.html (언어별 [복사] 버튼 — Play Console에 붙여 넣기용)

    python store/make_copy_page.py
"""
import html
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent / "listing"
ORDER = ["en-US", "ko-KR", "zh-CN", "zh-TW", "ja-JP", "fr-FR", "de-DE", "es-ES", "it-IT",
         "ru-RU", "pt-BR", "pl-PL", "tr-TR", "id", "vi"]
FIELDS = [("title", "App name"), ("short_description", "Short description"),
          ("full_description", "Full description"), ("release_notes", "Release notes")]


def sections(text: str) -> dict[str, str]:
    return {k: v.strip() for k, v in re.findall(r"\[(\w+)\]\n(.*?)(?=\n\[\w+\]\n|\Z)", text, re.S)}


def main() -> None:
    blocks = []
    for code in ORDER:
        sec = sections((HERE / f"{code}.txt").read_text(encoding="utf-8"))
        rows = []
        for key, label in FIELDS:
            value = sec.get(key, "")
            rows.append(
                f'<div class="field"><div class="head"><b>{label}</b> <span>{len(value)}</span>'
                f'<button onclick="copy(this)">Copy</button></div>'
                f'<textarea readonly rows="{3 if key != "full_description" else 10}">{html.escape(value)}</textarea></div>'
            )
        blocks.append(f'<section id="{code}"><h2>{code}</h2>{"".join(rows)}</section>')
    nav = " · ".join(f'<a href="#{c}">{c}</a>' for c in ORDER)
    page = f"""<!doctype html><html><head><meta charset="utf-8"><title>Store listing copy</title>
<style>
body {{ font: 15px/1.5 system-ui, "Malgun Gothic", sans-serif; margin: 0 auto; max-width: 900px; padding: 16px; background: #fff; color: #1f2328; }}
nav {{ position: sticky; top: 0; background: #fff; padding: 8px 0; border-bottom: 1px solid #ddd; }}
section {{ border-bottom: 2px solid #ddd; padding: 8px 0 16px; }}
.field {{ margin: 10px 0; }} .head {{ display: flex; gap: 8px; align-items: center; }}
.head span {{ color: #59636e; font-size: 13px; }} button {{ margin-left: auto; padding: 4px 14px; cursor: pointer; }}
button.done {{ background: #1a7f37; color: #fff; }}
textarea {{ width: 100%; box-sizing: border-box; font: inherit; }}
</style></head><body><nav>{nav}</nav>{"".join(blocks)}
<script>
function copy(btn) {{
  const ta = btn.closest('.field').querySelector('textarea');
  navigator.clipboard.writeText(ta.value).then(() => {{ btn.textContent = 'Copied'; btn.classList.add('done'); }});
}}
</script></body></html>"""
    (HERE / "copy.html").write_text(page, encoding="utf-8")


if __name__ == "__main__":
    main()
