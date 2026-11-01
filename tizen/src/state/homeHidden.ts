// 홈 "읽는 중에서 빼기" (Android 0.3.0~0.3.1 HomePreferences·ReadingRows).
// 뺀 시리즈는 "읽는 중"과 "다음 권" 두 줄에서 빠진다. 그 시리즈를 뺀 뒤에 다시 읽으면(서버의 readDate가 뺀 시각보다 뒤)
// 다시 보이고 기록을 지운다. 기록은 이 TV에만 있다 (시리즈 ID → 뺀 시각 ms).
import type { KomgaBook } from '../api/types';

const STORAGE_KEY = 'komgatv.home.hidden';

/** 저장된 문자열 → {시리즈 ID: 뺀 시각}. 깨진 값은 버린다 */
export function parseHidden(raw: string | null): Record<string, number> {
  if (!raw) return {};
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    const result: Record<string, number> = {};
    for (const [id, at] of Object.entries(parsed as Record<string, unknown>)) {
      if (typeof at === 'number' && Number.isFinite(at)) result[id] = at;
    }
    return result;
  } catch {
    return {};
  }
}

/** 서버가 준 마지막 읽은 시각 (ms). 모르면 0 */
export function readDateMillis(book: KomgaBook): number {
  const raw = book.readProgress?.readDate;
  const at = raw ? Date.parse(raw) : NaN;
  return Number.isFinite(at) ? at : 0;
}

export interface ReadingRows {
  reading: KomgaBook[];
  onDeck: KomgaBook[];
  /** 뺀 뒤에 다시 읽은 시리즈 — 다시 보이고 뺀 기록을 지운다 */
  reactivated: string[];
}

/** 뺀 시리즈를 두 줄에서 거른다 (순수 함수, 테스트용) */
export function filterReadingRows(
  reading: KomgaBook[],
  onDeck: KomgaBook[],
  hidden: Record<string, number>,
): ReadingRows {
  const reactivated: string[] = [];
  for (const book of reading) {
    const hiddenAt = hidden[book.seriesId];
    if (hiddenAt !== undefined && readDateMillis(book) > hiddenAt && reactivated.indexOf(book.seriesId) < 0) {
      reactivated.push(book.seriesId);
    }
  }
  const stillHidden = (seriesId: string) => hidden[seriesId] !== undefined && reactivated.indexOf(seriesId) < 0;
  return {
    reading: reading.filter((book) => !stillHidden(book.seriesId)),
    onDeck: onDeck.filter((book) => !stillHidden(book.seriesId)),
    reactivated,
  };
}

/**
 * 뺀 시각. TV 시계가 서버보다 늦어도 방금 읽은 책이 "다시 읽음"으로 보이지 않게 그 책의 읽은 시각보다 뒤로.
 */
export function hiddenAtFor(book: KomgaBook, now: number): number {
  return Math.max(now, readDateMillis(book));
}

export function loadHidden(): Record<string, number> {
  try {
    return parseHidden(localStorage.getItem(STORAGE_KEY));
  } catch {
    return {};
  }
}

function saveHidden(hidden: Record<string, number>): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(hidden));
  } catch {
    // 저장소를 못 쓰면 이번 세션에서만
  }
}

export function hideSeries(seriesId: string, at: number): void {
  saveHidden({ ...loadHidden(), [seriesId]: at });
}

export function unhideSeries(seriesIds: string[]): void {
  if (!seriesIds.length) return;
  const hidden = loadHidden();
  seriesIds.forEach((id) => delete hidden[id]);
  saveHidden(hidden);
}
