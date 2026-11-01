import { describe, expect, it } from 'vitest';
import type { KomgaBook } from '../src/api/types';
import { filterReadingRows, hiddenAtFor, parseHidden } from '../src/state/homeHidden';

const book = (id: string, seriesId: string, readDate?: string): KomgaBook =>
  ({
    id,
    seriesId,
    seriesTitle: seriesId,
    name: id,
    number: 1,
    media: { pagesCount: 10 },
    metadata: { title: id, number: '1' },
    readProgress: readDate ? { page: 3, completed: false, readDate } : null,
  }) as unknown as KomgaBook;

describe('읽는 중에서 빼기 (Android 0.3.0~0.3.1)', () => {
  const at = Date.parse('2026-11-01T08:00:00Z');

  it('뺀 시리즈는 읽는 중·다음 권 두 줄에서 빠진다', () => {
    const rows = filterReadingRows(
      [book('A1', 'A', '2026-11-01T07:00:00Z'), book('B1', 'B', '2026-11-01T07:00:00Z')],
      [book('A2', 'A'), book('C2', 'C')],
      { A: at },
    );
    expect(rows.reading.map((b) => b.id)).toEqual(['B1']);
    expect(rows.onDeck.map((b) => b.id)).toEqual(['C2']);
    expect(rows.reactivated).toEqual([]);
  });

  it('뺀 뒤에 다시 읽으면 다시 보이고 기록을 지운다', () => {
    const rows = filterReadingRows([book('A3', 'A', '2026-11-01T09:00:00Z')], [book('A4', 'A')], { A: at });
    expect(rows.reading.map((b) => b.id)).toEqual(['A3']);
    expect(rows.onDeck.map((b) => b.id)).toEqual(['A4']);
    expect(rows.reactivated).toEqual(['A']);
  });

  it('뺀 시각은 그 책의 읽은 시각보다 뒤 (TV 시계가 늦어도)', () => {
    const late = book('A1', 'A', '2026-11-01T09:00:00Z');
    expect(hiddenAtFor(late, at)).toBe(Date.parse('2026-11-01T09:00:00Z'));
    expect(hiddenAtFor(book('A2', 'A'), at)).toBe(at);
  });

  it('저장 값: 깨진 값은 버린다', () => {
    expect(parseHidden('{"A":1,"B":"x"}')).toEqual({ A: 1 });
    expect(parseHidden('not json')).toEqual({});
    expect(parseHidden(null)).toEqual({});
  });
});
