import { describe, expect, it } from 'vitest';
import { en, LANGUAGES, languageFor } from '../src/i18n';
import { ja, ptBR, zhCN, zhTW, id as indonesian } from '../src/locales';

describe('15개 언어 (Android 1.0.4와 같은 목록)', () => {
  it('언어 목록', () => {
    expect(LANGUAGES.map((language) => language.code)).toEqual([
      'ko', 'en', 'ja', 'zh-CN', 'zh-TW', 'de', 'fr', 'es', 'it', 'pt-BR', 'ru', 'pl', 'tr', 'id', 'vi',
    ]);
  });

  it('TV 언어 태그 → 앱 언어', () => {
    expect(languageFor('ja-JP')).toBe(ja);
    expect(languageFor('zh-CN')).toBe(zhCN);
    expect(languageFor('zh-TW')).toBe(zhTW);
    expect(languageFor('zh-Hant-HK')).toBe(zhTW);
    expect(languageFor('pt-PT')).toBe(ptBR);
    expect(languageFor('in-ID')).toBe(indonesian);
    expect(languageFor('id')).toBe(indonesian);
    expect(languageFor('xx-YY')).toBe(en);
  });

  it('모든 언어에 모든 문구, 자리 표시({0})는 영어와 같고 빈 문구 없음', () => {
    const marks = (s: string) => (s.match(/\{\d+\}/g) ?? []).sort().join(',');
    for (const language of LANGUAGES) {
      for (const [key, value] of Object.entries(en)) {
        const translated = (language.strings as Record<string, string>)[key];
        expect(translated, `${language.code}.${key}`).toBeTruthy();
        expect(marks(translated), `${language.code}.${key}`).toBe(marks(value));
      }
    }
  });

  it('앱 이름은 번역하지 않는다', () => {
    for (const language of LANGUAGES) expect(language.strings.appName).toBe('TV Reader for Komga');
  });
});
