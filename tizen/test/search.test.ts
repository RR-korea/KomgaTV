import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type SearchState, SearchController } from '../src/state/searchController';

describe('SearchController', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  const setup = (search: (q: string) => Promise<string>) => {
    const states: SearchState<string>[] = [];
    const controller = new SearchController(search, (s) => states.push(s));
    return { controller, states };
  };

  it('입력이 멈추고 400ms 뒤 마지막 검색어로 한 번만 찾는다', async () => {
    const search = vi.fn(async (q: string) => `r:${q}`);
    const { controller, states } = setup(search);
    controller.input('ki');
    await vi.advanceTimersByTimeAsync(200);
    controller.input('king');
    await vi.advanceTimersByTimeAsync(399);
    expect(search).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(search).toHaveBeenCalledTimes(1);
    expect(search).toHaveBeenCalledWith('king');
    expect(states[states.length - 1]).toEqual({ kind: 'done', query: 'king', result: 'r:king' });
  });

  it('앞뒤 공백만 다르면 다시 찾지 않는다', async () => {
    const search = vi.fn(async (q: string) => q);
    const { controller } = setup(search);
    controller.input('킹덤');
    await vi.advanceTimersByTimeAsync(400);
    controller.input(' 킹덤 ');
    await vi.advanceTimersByTimeAsync(400);
    expect(search).toHaveBeenCalledTimes(1);
  });

  it('빈 입력은 처음 상태', async () => {
    const { controller, states } = setup(async (q) => q);
    controller.input('a');
    controller.input('   ');
    await vi.advanceTimersByTimeAsync(400);
    expect(states).toEqual([{ kind: 'idle' }]);
  });

  it('늦게 도착한 이전 결과는 버린다', async () => {
    let resolveFirst: (v: string) => void = () => undefined;
    const search = vi
      .fn<(q: string) => Promise<string>>()
      .mockImplementationOnce(() => new Promise((r) => (resolveFirst = r)))
      .mockImplementation(async (q) => `r:${q}`);
    const { controller, states } = setup(search);
    controller.input('a');
    await vi.advanceTimersByTimeAsync(400);
    controller.input('ab');
    await vi.advanceTimersByTimeAsync(400);
    resolveFirst('stale');
    await vi.advanceTimersByTimeAsync(0);
    expect(states.filter((s) => s.kind === 'done')).toEqual([{ kind: 'done', query: 'ab', result: 'r:ab' }]);
  });

  it('오류 → 다시 시도', async () => {
    const search = vi.fn<(q: string) => Promise<string>>().mockRejectedValueOnce(new Error('x')).mockResolvedValue('ok');
    const { controller, states } = setup(search);
    controller.input('q');
    await vi.advanceTimersByTimeAsync(400);
    expect(states[states.length - 1].kind).toBe('error');
    controller.retry();
    await vi.advanceTimersByTimeAsync(400);
    expect(states[states.length - 1]).toEqual({ kind: 'done', query: 'q', result: 'ok' });
  });
});
