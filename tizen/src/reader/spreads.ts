// 펼침(두 쪽 보기) 분할. Komelia(Apache-2.0) PagedReaderState.kt의 알고리즘을 옮겼다 — Android T5와 같은 규칙.
// Source: https://github.com/Snd-R/Komelia
// File: komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/paged/PagedReaderState.kt
// License: Apache License 2.0 (tizen/THIRD_PARTY_NOTICES.md)
import type { PageLayout } from '../state/readerPrefs';

export interface PageInfo {
  /** 1부터 */
  number: number;
  width: number | null;
  height: number | null;
}

/** 한 번에 보이는 1~2쪽 */
export type Spread = PageInfo[];

export const isLandscape = (page: PageInfo): boolean =>
  page.width !== null && page.height !== null && page.width > page.height;

export const highestPage = (spread: Spread): number => Math.max(0, ...spread.map((page) => page.number));

export const spreadContains = (spread: Spread, pageNumber: number): boolean =>
  spread.some((page) => page.number === pageNumber);

interface Segment {
  singles: PageInfo[];
  landscape: PageInfo | null;
}

function segments(pages: PageInfo[], withCover: boolean): Segment[] {
  const result: Segment[] = [];
  let rest = pages;
  if (withCover && pages.length > 0) {
    result.push({ singles: [pages[0]], landscape: null });
    rest = pages.slice(1);
  }
  let singles: PageInfo[] = [];
  for (const page of rest) {
    if (isLandscape(page)) {
      result.push({ singles, landscape: page });
      singles = [];
    } else {
      singles.push(page);
    }
  }
  if (singles.length) result.push({ singles, landscape: null });
  return result;
}

function chunkPairs(pages: PageInfo[]): Spread[] {
  const spreads: Spread[] = [];
  for (let i = 0; i < pages.length; i += 2) spreads.push(pages.slice(i, i + 2));
  return spreads;
}

/**
 * 페이지 목록 → 펼침 목록.
 * - SINGLE_PAGE: 한 쪽씩
 * - DOUBLE_PAGES: 첫 쪽(표지) 단독, 이후 둘씩. 가로 쪽은 단독.
 * - DOUBLE_PAGES_NO_COVER: 처음부터 둘씩
 * - offset: 각 구간의 첫 쪽을 단독으로 두어 짝을 한 쪽 민다 (세션 동안만)
 */
export function buildSpreads(pages: PageInfo[], layout: PageLayout, offset = false): Spread[] {
  if (!pages.length) return [];
  if (layout === 'SINGLE_PAGE') return pages.map((page) => [page]);
  const spreads: Spread[] = [];
  for (const segment of segments(pages, layout === 'DOUBLE_PAGES')) {
    if (offset && segment.singles.length) {
      spreads.push([segment.singles[0]]);
      spreads.push(...chunkPairs(segment.singles.slice(1)));
    } else {
      spreads.push(...chunkPairs(segment.singles));
    }
    if (segment.landscape) spreads.push([segment.landscape]);
  }
  return spreads;
}

/** 숨긴 쪽을 뺀 쪽들 (Android 1.0.8 visiblePages) — 모두 숨겼으면 전부 */
export function withoutHidden(pages: PageInfo[], hidden: number[]): PageInfo[] {
  if (!hidden.length) return pages;
  const shown = pages.filter((page) => hidden.indexOf(page.number) < 0);
  return shown.length ? shown : pages;
}

/** 그 쪽(숨긴 쪽이면 다음 쪽)의 자리 — 끝을 넘으면 마지막 쪽, 없으면 0 */
export function pageIndexAtOrAfter(pages: PageInfo[], pageNumber: number): number {
  const index = pages.findIndex((page) => page.number >= pageNumber);
  return index >= 0 ? index : Math.max(0, pages.length - 1);
}

/** 그 쪽이 든 펼침 (숨긴 쪽이면 다음 펼침, 끝을 넘으면 마지막 펼침, 없으면 0) — Android spreadIndexAtOrAfter */
export function spreadIndexAtOrAfter(spreads: Spread[], pageNumber: number): number {
  const index = spreads.findIndex((spread) => spread.some((page) => page.number >= pageNumber));
  return index >= 0 ? index : Math.max(0, spreads.length - 1);
}

/** 쪽 번호가 들어 있는 펼침 인덱스 (없으면 0) */
export function spreadIndexOf(spreads: Spread[], pageNumber: number): number {
  const index = spreads.findIndex((spread) => spreadContains(spread, pageNumber));
  return index >= 0 ? index : 0;
}
