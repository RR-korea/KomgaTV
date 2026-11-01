import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { bookDisplayTitle } from '../src/api/display';
import type { KomgaBook, R2Progression } from '../src/api/types';
import {
  chaptersOf,
  currentTocIndex,
  tocEntries,
  EpubPositionSaver,
  progressionOf,
  resourcePath,
  startPosition,
  stepZoom,
  toProgression,
  totalProgression,
} from '../src/epub/epub';

const base = 'https://h/komga/api/v1/books/B1/resource/';
const manifest = {
  readingOrder: [0, 1, 2].map((i) => ({ href: `${base}index_split_00${i}.html`, type: 'application/xhtml+xml' })),
  toc: [{ href: `${base}index_split_001.html#c1`, title: '1장' }],
};
const chapters = chaptersOf(manifest);
const progression = (href: string, p: number): R2Progression => ({
  modified: '',
  device: { id: 'x', name: 'x' },
  locator: { href, type: 'application/xhtml+xml', locations: { progression: p, totalProgression: 0.5 } },
});

describe('EPUB 장·경로', () => {
  it('전체 URL → 책 안 경로 (Komga는 이것만 받는다)', () => {
    expect(resourcePath(`${base}index_split_001.html`)).toBe('index_split_001.html');
    expect(resourcePath(`${base}Text%2Fch%201.xhtml`)).toBe('Text/ch 1.xhtml');
    expect(resourcePath('plain.html')).toBe('plain.html');
  });

  it('목차 제목을 장에 붙인다', () => {
    expect(chapters.map((c) => c.title)).toEqual([undefined, '1장', undefined]);
  });
});

describe('시작 위치', () => {
  it('저장된 장·위치에서 연다', () => {
    expect(startPosition(chapters, { page: 3, completed: false }, progression('index_split_001.html', 0.4))).toEqual({
      chapter: 1,
      progression: 0.4,
    });
  });

  it('다 읽은 책, 읽은 적 없는 책, 없는 장은 처음부터', () => {
    expect(startPosition(chapters, { page: 9, completed: true }, progression('index_split_002.html', 0.9))).toEqual({
      chapter: 0,
      progression: 0,
    });
    expect(startPosition(chapters, null, null)).toEqual({ chapter: 0, progression: 0 });
    expect(startPosition(chapters, null, progression('gone.html', 0.5))).toEqual({ chapter: 0, progression: 0 });
  });
});

describe('위치 계산', () => {
  it('장 안 위치와 전체 위치', () => {
    expect(progressionOf(0, 5000, 1000)).toBe(0);
    expect(progressionOf(2000, 5000, 1000)).toBe(0.5);
    expect(progressionOf(0, 800, 1000)).toBe(1); // 한 화면에 다 보이면 끝
    expect(totalProgression({ chapter: 1, progression: 0.5 }, 3)).toBeCloseTo(0.5);
  });

  it('저장 형식: 책 안 경로 + 위치', () => {
    const saved = toProgression(chapters, { chapter: 2, progression: 0.25 }, new Date('2026-11-01T00:00:00Z'));
    expect(saved.locator.href).toBe('index_split_002.html');
    expect(saved.locator.locations).toEqual({ progression: 0.25, totalProgression: 0.75 });
    expect(saved.modified).toBe('2026-11-01T00:00:00.000Z');
  });

  it('글자 크기 60~200%, 10씩', () => {
    expect(stepZoom(100, 1)).toBe(110);
    expect(stepZoom(200, 1)).toBe(200);
    expect(stepZoom(60, -1)).toBe(60);
  });
});

describe('EpubPositionSaver', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('2초 디바운스, 나갈 때 즉시', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const saver = new EpubPositionSaver(save);
    saver.schedule({ chapter: 0, progression: 0.1 });
    saver.schedule({ chapter: 0, progression: 0.2 });
    await vi.advanceTimersByTimeAsync(2000);
    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith({ chapter: 0, progression: 0.2 });
    saver.schedule({ chapter: 1, progression: 0 });
    await saver.flush();
    expect(save).toHaveBeenCalledTimes(2);
  });
});

describe('파일 해시 제목', () => {
  it('제목이 긴 16진수면 시리즈 제목 (Android T12)', () => {
    const book = {
      seriesTitle: '걸그룹인데 악마와 계약했습니다',
      name: '걸그룹인데-001-160',
      metadata: { title: 'a67879a1d4464860c3eedc980bd12118', number: '1' },
    } as KomgaBook;
    expect(bookDisplayTitle(book)).toBe('걸그룹인데 악마와 계약했습니다');
  });
});

describe('EPUB 목차 (Android T13)', () => {
  const label = (n: number) => `${n}장`;
  const toc = (entries: { title: string; i: number; children?: { title: string; i: number }[] }[]) => ({
    readingOrder: manifest.readingOrder,
    toc: entries.map((e) => ({
      title: e.title,
      href: `${base}index_split_00${e.i}.html#x`,
      children: e.children?.map((c) => ({ title: c.title, href: `${base}index_split_00${c.i}.html` })),
    })),
  });

  it('하위 항목을 들여쓰기 단계와 함께 한 줄로 편다', () => {
    const entries = tocEntries(toc([{ title: '1부', i: 0, children: [{ title: '2장', i: 1 }] }, { title: '3장', i: 2 }]), chapters, label);
    expect(entries).toEqual([
      { title: '1부', depth: 0, chapter: 0 },
      { title: '2장', depth: 1, chapter: 1 },
      { title: '3장', depth: 0, chapter: 2 },
    ]);
  });

  it('목차가 없거나 한 줄뿐이면 장마다 한 줄', () => {
    const one = tocEntries(toc([{ title: 'Start', i: 0 }]), chapters, label);
    expect(one.map((e) => e.title)).toEqual(['1장', '2장', '3장']);
    expect(tocEntries({ readingOrder: manifest.readingOrder }, chapters, label)).toHaveLength(3);
  });

  it('지금 장의 줄 — 목차에 없는 장이면 앞 장의 줄', () => {
    const entries = tocEntries(toc([{ title: 'A', i: 0 }, { title: 'C', i: 2 }]), chapters, label);
    expect(currentTocIndex(entries, 1)).toBe(0);
    expect(currentTocIndex(entries, 2)).toBe(1);
  });
});
