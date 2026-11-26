import { describe, expect, it } from 'vitest';
import type { KomgaBook } from '../src/api/types';
import { filterReadingRows, hiddenAtFor, mergeReadingRow, parseHidden } from '../src/state/homeHidden';

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

  it('뺀 시리즈는 읽는 중 줄에서 빠진다', () => {
    const rows = filterReadingRows(
      [book('A1', 'A', '2026-11-01T07:00:00Z'), book('B1', 'B', '2026-11-01T07:00:00Z'), book('A2', 'A')],
      { A: at },
    );
    expect(rows.reading.map((b) => b.id)).toEqual(['B1']);
    expect(rows.reactivated).toEqual([]);
  });

  it('뺀 뒤에 다시 읽으면 다시 보이고 기록을 지운다', () => {
    const rows = filterReadingRows([book('A3', 'A', '2026-11-01T09:00:00Z')], { A: at });
    expect(rows.reading.map((b) => b.id)).toEqual(['A3']);
    expect(rows.reactivated).toEqual(['A']);
  });

  it('뺀 시각은 그 책의 읽은 시각보다 뒤 (TV 시계가 늦어도)', () => {
    const late = book('A1', 'A', '2026-11-01T09:00:00Z');
    expect(hiddenAtFor(late, at)).toBe(Date.parse('2026-11-01T09:00:00Z'));
    expect(hiddenAtFor(book('A2', 'A'), at)).toBe(at);
  });

  it('끝까지 읽은 시리즈는 다음 권으로 읽는 중에 남는다 (Android 1.0.47)', () => {
    const inProgress = [book('A1', 'A', '2026-11-01T07:00:00Z')];
    const read = [
      book('B3', 'B', '2026-11-01T09:00:00Z'), // 방금 다 읽음 → 다음 권 B4, 맨 앞
      book('B2', 'B', '2026-10-30T09:00:00Z'), // 같은 시리즈는 한 번만
      book('A0', 'A', '2026-10-01T09:00:00Z'), // 읽던 책이 있는 시리즈는 그 책만
      book('C9', 'C', '2026-10-20T09:00:00Z'), // 다음 권이 아직 없다 → 마지막으로 읽은 책
    ];
    const onDeck = [book('B4', 'B'), book('A2', 'A')];
    expect(mergeReadingRow(inProgress, read, onDeck, 20).map((b) => b.id)).toEqual(['B4', 'A1', 'C9']);
    expect(mergeReadingRow(inProgress, read, onDeck, 2).map((b) => b.id)).toEqual(['B4', 'A1']);
    expect(mergeReadingRow([], [], onDeck, 20)).toEqual([]);
  });

  it('저장 값: 깨진 값은 버린다', () => {
    expect(parseHidden('{"A":1,"B":"x"}')).toEqual({ A: 1 });
    expect(parseHidden('not json')).toEqual({});
    expect(parseHidden(null)).toEqual({});
  });
});
