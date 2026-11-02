import { describe, expect, it } from 'vitest';
import { parseServerList, removeServerFrom, type SavedServer, serverId, upsertServer } from '../src/state/session';

const basic = (url: string, email: string, password = 'pw'): SavedServer => ({
  url,
  auth: { kind: 'basic', email, password },
});
const apiKey = (url: string, key: string): SavedServer => ({ url, auth: { kind: 'apiKey', apiKey: key } });

describe('저장된 서버 목록 (Android T11)', () => {
  it('같은 서버·같은 계정이면 같은 ID (주소 끝 슬래시·이메일 대소문자 무시)', () => {
    expect(serverId(basic('https://h/komga/', 'A@B.c'))).toBe(serverId(basic('https://h/komga', 'a@b.c')));
    expect(serverId(basic('https://h/komga', 'a@b.c'))).not.toBe(serverId(basic('https://h/komga', 'x@b.c')));
  });

  it('API 키는 서버당 하나', () => {
    expect(serverId(apiKey('https://h', 'k1'))).toBe(serverId(apiKey('https://h/', 'k2')));
  });

  it('같은 ID면 바꾸고, 아니면 끝에 더한다', () => {
    const list = [basic('https://a', 'u@a', 'old'), basic('https://b', 'u@b')];
    const replaced = upsertServer(list, basic('https://a/', 'u@a', 'new'));
    expect(replaced).toHaveLength(2);
    expect(replaced[0].auth).toEqual({ kind: 'basic', email: 'u@a', password: 'new' });
    expect(upsertServer(list, apiKey('https://c', 'k'))).toHaveLength(3);
  });

  it('삭제', () => {
    const list = [basic('https://a', 'u@a'), basic('https://b', 'u@b')];
    expect(removeServerFrom(list, serverId(list[0]))).toEqual([list[1]]);
  });

  it('깨진 저장값·항목은 버린다', () => {
    expect(parseServerList(null)).toEqual([]);
    expect(parseServerList('not json')).toEqual([]);
    expect(parseServerList('{"url":"x"}')).toEqual([]);
    const good = basic('https://a', 'u@a');
    expect(parseServerList(JSON.stringify([good, { url: 'x' }, { url: 'y', auth: { kind: 'apiKey' } }]))).toEqual([good]);
  });
});

describe('새 서버를 저장해도 지금 서버가 남는다 (Android 1.0.5)', () => {
  it('목록이 생기기 전 버전에서 연결한 서버(목록에 없음)도 남는다', async () => {
    const store = new Map<string, string>();
    const original = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: {
        getItem: (k: string) => store.get(k) ?? null,
        setItem: (k: string, v: string) => void store.set(k, v),
        removeItem: (k: string) => void store.delete(k),
      },
    });
    try {
      const { saveServer, loadServerList } = await import('../src/state/session');
      const old = basic('https://old/komga', 'a@b.c');
      store.set('komgatv.server', JSON.stringify(old)); // 예전 버전: 지금 서버만 있고 목록은 없다
      saveServer(basic('https://new/komga', 'x@y.z'));
      expect(loadServerList().map((server) => server.url)).toEqual(['https://old/komga', 'https://new/komga']);
    } finally {
      if (original) Object.defineProperty(globalThis, 'localStorage', original);
      else delete (globalThis as { localStorage?: unknown }).localStorage;
    }
  });
});
