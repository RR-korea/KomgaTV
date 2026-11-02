"""타이젠 UI 문자열 13개 언어(src/locales/*.ts) 만들기 — Android 번역을 그대로 가져온다.

- 한국어·영어 원문은 src/i18n.ts (ko, en 표).
- 영어(또는 한국어) 문구가 Android strings.xml과 같으면 그 문자열의 Android 번역을 쓴다 (%1$s → {0}).
- 타이젠에만 있는 문구는 dev/locales-manual.json (사람이 고친 번역).
- 몇몇은 Android 문구에서 떼어 쓴다 (보기 설정, 닫기).

    python dev/gen-locales.py <Android 저장소 루트, 예: D:/Project/KomgaTV>
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
TIZEN = HERE.parent
ANDROID = Path(sys.argv[1])
MODULES = ['app', 'core/ui', 'feature/home', 'feature/library', 'feature/reader', 'feature/search', 'feature/settings']
# 타이젠 코드 → Android 리소스 폴더 접미사, TS 이름
LOCALES = {
    'de': ('de', 'de'), 'es': ('es', 'es'), 'fr': ('fr', 'fr'), 'id': ('in', 'id'), 'it': ('it', 'it'),
    'ja': ('ja', 'ja'), 'pl': ('pl', 'pl'), 'pt-BR': ('pt-rBR', 'ptBR'), 'ru': ('ru', 'ru'), 'tr': ('tr', 'tr'),
    'vi': ('vi', 'vi'), 'zh-CN': ('zh-rCN', 'zhCN'), 'zh-TW': ('zh-rTW', 'zhTW'),
}
# 번역하지 않는 값 (앱 이름)
FIXED = {'appName', 'aboutAppVersion'}
# 이름으로 직접 잇는 Android 문자열 (영어 원문이 조금 달라도 같은 뜻)
BY_NAME = {
    'settingsDemoServer': 'settings_demo_server',
    'settingsDemoHint': 'settings_demo_hint',
    'aboutDeveloperBio': 'about_developer_bio',
    'aboutDeveloperWish': 'about_developer_wish',
}


def unescape(s: str) -> str:
    s = s.strip()
    if len(s) >= 2 and s[0] == '"' and s[-1] == '"':
        s = s[1:-1]
    return s.replace("\\'", "'").replace('\\"', '"').replace('\\n', '\n').replace('\\@', '@').replace('\\?', '?')


def braces(s: str) -> str:
    s = re.sub(r'%(\d+)\$[sd]', lambda m: '{%d}' % (int(m.group(1)) - 1), s)
    n = iter(range(100))
    s = re.sub(r'%[sd]', lambda m: '{%d}' % next(n), s)
    return s.replace('%%', '%')


def android(suffix: str) -> dict:
    out = {}
    for module in MODULES:
        f = ANDROID / module / 'src/main/res' / f'values{suffix}' / 'strings.xml'
        if not f.exists():
            continue
        for el in ET.parse(f).getroot():
            if el.tag == 'string':
                out[el.get('name')] = braces(unescape(''.join(el.itertext())))
            elif el.tag == 'plurals':
                # 타이젠은 복수형을 나누지 않는다 — 'other' 꼴을 쓴다
                forms = {item.get('quantity'): braces(unescape(''.join(item.itertext()))) for item in el}
                out[el.get('name')] = forms.get('other') or next(iter(forms.values()))
    return out


def tizen_table(name: str) -> dict:
    """src/i18n.ts의 ko/en 표를 읽는다 (한 줄에 key: '값', 형식)"""
    text = (TIZEN / 'src/i18n.ts').read_text(encoding='utf-8')
    start = re.search(rf'^export const {name}\b.*\{{$', text, re.M).end()
    end = text.index('\n};', start)
    table = {}
    for line in text[start:end].splitlines():
        m = re.match(r"^  (\w+): (['\"])(.*)\2,$", line)
        if m:
            value = m.group(3)
            value = value.replace("\\'", "'") if m.group(2) == "'" else value.replace('\\"', '"')
            table[m.group(1)] = value
    return table


def placeholders(s: str) -> set:
    return set(re.findall(r'\{\d+\}', s))


def ts_string(s: str) -> str:
    return "'" + s.replace('\\', '\\\\').replace("'", "\\'").replace('\n', '\\n') + "'"


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    en, ko = tizen_table('en'), tizen_table('ko')
    assert list(en) == list(ko), 'ko·en 키가 다르다'
    a_en, a_ko = android(''), android('-ko')
    by_en, by_ko = {}, {}
    for k, v in a_en.items():
        by_en.setdefault(v, k)
    for k, v in a_ko.items():
        by_ko.setdefault(v, k)
    manual = json.loads((HERE / 'locales-manual.json').read_text(encoding='utf-8'))
    out_dir = TIZEN / 'src/locales'
    out_dir.mkdir(exist_ok=True)
    index = []
    problems = []
    for code, (suffix, ident) in LOCALES.items():
        table = android('-' + suffix)
        derived = {}
        title = table.get('reader_settings_title_series')
        if title:
            derived['readerSettings'] = title.split(' · ')[0]
        hint = table.get('reader_settings_hint', '')
        m = re.search(r'Back\s*[:：]\s*(.+)$', hint)
        if m:
            derived['close'] = m.group(1).strip()
        lines = [f"// {code} — dev/gen-locales.py가 Android 번역에서 만든 파일. 직접 고치지 말고 스크립트·locales-manual.json을 고친다.",
                 "import type { Strings } from '../i18n';", '', f'export const {ident}: Strings = {{']
        for key, en_text in en.items():
            if key in FIXED:
                value = en_text
            elif key in manual.get(code, {}):
                value = manual[code][key]
            elif key in derived:
                value = derived[key]
            else:
                name = BY_NAME.get(key) or by_en.get(en_text) or by_ko.get(ko[key])
                value = table.get(name) if name else None
            if value is None:
                problems.append(f'{code}: {key} 번역 없음')
                value = en_text
            if placeholders(value) != placeholders(en_text):
                problems.append(f'{code}: {key} 자리 표시가 다르다 {placeholders(value)} vs {placeholders(en_text)}')
            lines.append(f'  {key}: {ts_string(value)},')
        lines.append('};')
        (out_dir / f'{code}.ts').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')
        index.append(f"export {{ {ident} }} from './{code}';")
    (out_dir / 'index.ts').write_text('\n'.join(index) + '\n', encoding='utf-8', newline='\n')
    print('\n'.join(problems) if problems else 'ok — 13개 언어, 빠진 번역 없음')
    if problems:
        sys.exit(1)


if __name__ == '__main__':
    main()
