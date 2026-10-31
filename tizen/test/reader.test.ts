import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { layoutSpread } from '../src/reader/layout';
import { ProgressSaver } from '../src/reader/progress';

const screen = { width: 1920, height: 1080 };
const portrait = { width: 1000, height: 1500 };
const small = { width: 400, height: 600 };

describe('layoutSpread', () => {
  it('SCREEN: 한 쪽은 높이에 맞춘다', () => {
    const layout = layoutSpread([portrait], 'SCREEN', true, screen);
    expect(layout.height).toBe(1080);
    expect(layout.pages[0].width).toBe(720);
  });

  it('SCREEN: 두 쪽은 같은 높이로 나란히, 화면 안', () => {
    const layout = layoutSpread([portrait, portrait], 'SCREEN', true, screen);
    expect(layout.height).toBe(1080);
    expect(layout.width).toBe(1440);
  });

  it('SCREEN: 넓은 쪽은 너비에 맞춘다', () => {
    const layout = layoutSpread([{ width: 3000, height: 1000 }], 'SCREEN', true, screen);
    expect(layout.width).toBe(1920);
    expect(layout.height).toBe(640);
  });

  it('FIT_WIDTH: 화면 너비를 채우고 높이는 넘칠 수 있다', () => {
    const layout = layoutSpread([portrait], 'FIT_WIDTH', true, screen);
    expect(layout.width).toBe(1920);
    expect(layout.height).toBe(2880);
  });

  it('FIT_HEIGHT: 높이를 채운다', () => {
    expect(layoutSpread([{ width: 3000, height: 1000 }], 'FIT_HEIGHT', true, screen).height).toBe(1080);
  });

  it('ORIGINAL: 원본 크기', () => {
    const layout = layoutSpread([portrait], 'ORIGINAL', true, screen);
    expect(layout.pages[0]).toEqual(portrait);
  });

  it('작은 이미지 키우기 꺼짐: 원본보다 크게 그리지 않는다', () => {
    expect(layoutSpread([small], 'SCREEN', false, screen).height).toBe(600);
    expect(layoutSpread([small], 'FIT_WIDTH', false, screen).height).toBe(600);
    expect(layoutSpread([small], 'SCREEN', true, screen).height).toBe(1080);
  });
});

describe('ProgressSaver', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('2초 디바운스: 마지막 쪽만 한 번 저장', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const saver = new ProgressSaver(save, null);
    saver.schedule(2, false);
    saver.schedule(3, false);
    await vi.advanceTimersByTimeAsync(1999);
    expect(save).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith(3, false);
  });

  it('나갈 때(flush) 기다리지 않고 바로 저장', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const saver = new ProgressSaver(save, null);
    saver.schedule(5, true);
    await saver.flush();
    expect(save).toHaveBeenCalledWith(5, true);
    await vi.advanceTimersByTimeAsync(5000);
    expect(save).toHaveBeenCalledTimes(1);
  });

  it('옮긴 적 없거나 저장된 값과 같으면 저장하지 않는다', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const saver = new ProgressSaver(save, { page: 7, completed: false });
    await saver.flush();
    saver.schedule(7, false);
    await saver.flush();
    expect(save).not.toHaveBeenCalled();
  });

  it('저장 실패는 삼키고 다음 저장을 막지 않는다', async () => {
    const save = vi.fn().mockRejectedValueOnce(new Error('net')).mockResolvedValue(undefined);
    const saver = new ProgressSaver(save, null);
    saver.schedule(2, false);
    await saver.flush();
    saver.schedule(2, false);
    await saver.flush();
    expect(save).toHaveBeenCalledTimes(2);
  });
});
