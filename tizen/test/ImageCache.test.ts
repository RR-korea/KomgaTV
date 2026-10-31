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
