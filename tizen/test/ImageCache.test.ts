import { describe, expect, it } from 'vitest';
import { ImageCache } from '../src/image/ImageCache';

function makeCache(maxIdle: number) {
  const loads: string[] = [];
  const revoked: string[] = [];
  let seq = 0;
  const cache = new ImageCache(
    async (key) => {
      loads.push(key);
      return new Blob([key]);
    },
    maxIdle,
    () => `blob:${++seq}`,
    (url) => revoked.push(url),
  );
  return { cache, loads, revoked };
}

describe('ImageCache', () => {
  it('같은 키는 한 번만 받고 같은 URL을 준다', async () => {
    const { cache, loads } = makeCache(10);
    const [a, b] = await Promise.all([cache.acquire('k'), cache.acquire('k')]);
    expect(a).toBe(b);
    expect(loads).toEqual(['k']);
  });

  it('참조 중인 항목은 지우지 않고, 참조 없는 항목만 오래된 순으로 해제한다', async () => {
    const { cache, revoked } = makeCache(1);
    const held = await cache.acquire('held');
    const old = await cache.acquire('old');
    cache.release('old');
    await cache.acquire('new');
    cache.release('new');
    // idle = old, new → maxIdle 1 → old 해제, held는 참조 중이라 유지
    expect(revoked).toEqual([old]);
    expect(cache.size).toBe(2);
    expect(await cache.acquire('held')).toBe(held);
  });

  it('prefetch는 참조를 남기지 않는다', async () => {
    const { cache, loads } = makeCache(5);
    cache.prefetch('p');
    cache.prefetch('p');
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(loads).toEqual(['p']);
    expect(cache.size).toBe(1);
  });

  it('받기 실패는 호출한 쪽으로 전파되고 재시도할 수 있다', async () => {
    let fail = true;
    const cache = new ImageCache(
      async (key) => {
        if (fail) throw new Error('boom');
        return new Blob([key]);
      },
      5,
      () => 'blob:1',
      () => undefined,
    );
    await expect(cache.acquire('k')).rejects.toThrow('boom');
    fail = false;
    expect(await cache.acquire('k')).toBe('blob:1');
  });

  it('clear는 전부 해제한다', async () => {
    const { cache, revoked } = makeCache(5);
    await cache.acquire('a');
    await cache.acquire('b');
    cache.clear();
    expect(revoked).toHaveLength(2);
    expect(cache.size).toBe(0);
  });
});

describe('ImageCache 대기열 (Android 이미지 대기열 분리·미리 받기 취소)', () => {
  /** 손으로 끝내는 로더: 시작된 키와 끝내기 함수 */
  function manual() {
    const started: string[] = [];
    const finish = new Map<string, () => void>();
    const aborted: string[] = [];
    const load = (key: string, signal: AbortSignal) =>
      new Promise<Blob>((resolve, reject) => {
        started.push(key);
        finish.set(key, () => resolve(new Blob([key])));
        signal.addEventListener('abort', () => {
          aborted.push(key);
          reject(Object.assign(new Error('aborted'), { name: 'AbortError' }));
        });
      });
    let n = 0;
    const cache = new ImageCache(load, 10, () => `blob:${++n}`, () => undefined, 2);
    return { cache, started, finish, aborted };
  }
  const tick = () => new Promise((r) => setTimeout(r, 0));

  it('동시에 받는 수를 넘지 않고, 화면에 보일 그림이 미리 받기보다 먼저', async () => {
    const { cache, started, finish } = manual();
    cache.prefetch('p1');
    cache.prefetch('p2');
    cache.prefetch('p3');
    const shown = cache.acquire('shown');
    await tick();
    expect(started).toEqual(['p1', 'p2']); // 2개까지
    finish.get('p1')!();
    await tick();
    expect(started).toEqual(['p1', 'p2', 'shown']); // p3보다 먼저
    finish.get('shown')!();
    expect(await shown).toMatch(/^blob:/);
  });

  it('이웃이 아니게 된 미리 받기는 취소 (대기 중이든 받는 중이든), 화면 그림은 그대로', async () => {
    const { cache, started, aborted } = manual();
    cache.prefetch('a');
    cache.prefetch('b');
    cache.prefetch('c'); // 대기 중
    const shown = cache.acquire('b'); // 미리 받던 b가 화면에 보이게 됨
    cache.retainPrefetches([]);
    await tick();
    expect(aborted).toEqual(['a']); // 받는 중이던 a 취소, b는 화면 그림이라 유지
    expect(started).not.toContain('c'); // 대기 중이던 c는 시작도 안 함
    expect(cache.size).toBe(0);
    void shown;
  });

  it('취소된 그림은 다시 요청하면 새로 받는다', async () => {
    const { cache, started, finish } = manual();
    cache.prefetch('x');
    cache.retainPrefetches([]);
    await tick();
    const again = cache.acquire('x');
    await tick();
    expect(started.filter((k) => k === 'x')).toHaveLength(2);
    finish.get('x')!();
    expect(await again).toMatch(/^blob:/);
  });
});
