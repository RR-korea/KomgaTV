import { describe, expect, it } from 'vitest';
import { buildSpreads, highestPage, type PageInfo, spreadIndexOf } from '../src/reader/spreads';

const portrait = (number: number): PageInfo => ({ number, width: 800, height: 1200 });
const landscape = (number: number): PageInfo => ({ number, width: 1600, height: 1200 });
const unknown = (number: number): PageInfo => ({ number, width: null, height: null });
const numbers = (spreads: PageInfo[][]) => spreads.map((spread) => spread.map((page) => page.number));

describe('buildSpreads', () => {
  const five = [1, 2, 3, 4, 5].map(portrait);

  it('SINGLE_PAGE는 한 쪽씩', () => {
    expect(numbers(buildSpreads(five, 'SINGLE_PAGE'))).toEqual([[1], [2], [3], [4], [5]]);
  });

  it('DOUBLE_PAGES는 표지 단독, 홀수 끝은 단독', () => {
    expect(numbers(buildSpreads(five, 'DOUBLE_PAGES'))).toEqual([[1], [2, 3], [4, 5]]);
    expect(numbers(buildSpreads([1, 2, 3, 4].map(portrait), 'DOUBLE_PAGES'))).toEqual([[1], [2, 3], [4]]);
  });

  it('DOUBLE_PAGES_NO_COVER는 처음부터 둘씩', () => {
    expect(numbers(buildSpreads(five, 'DOUBLE_PAGES_NO_COVER'))).toEqual([[1, 2], [3, 4], [5]]);
  });

  it('offset은 짝을 한 쪽 민다', () => {
    expect(numbers(buildSpreads(five, 'DOUBLE_PAGES', true))).toEqual([[1], [2], [3, 4], [5]]);
    expect(numbers(buildSpreads(five, 'DOUBLE_PAGES_NO_COVER', true))).toEqual([[1], [2, 3], [4, 5]]);
  });

  it('가로 쪽은 단독이고 구간을 끊는다', () => {
    const pages = [portrait(1), portrait(2), portrait(3), landscape(4), portrait(5), portrait(6)];
    expect(numbers(buildSpreads(pages, 'DOUBLE_PAGES_NO_COVER'))).toEqual([[1, 2], [3], [4], [5, 6]]);
  });

  it('크기를 모르는 쪽은 세로로 본다', () => {
    expect(numbers(buildSpreads([1, 2, 3].map(unknown), 'DOUBLE_PAGES_NO_COVER'))).toEqual([[1, 2], [3]]);
  });

  it('빈 목록', () => {
    expect(buildSpreads([], 'DOUBLE_PAGES')).toEqual([]);
  });
});

describe('spreadIndexOf / highestPage', () => {
  it('쪽이 든 펼침을 찾고 없으면 0', () => {
    const spreads = buildSpreads([1, 2, 3, 4, 5].map(portrait), 'DOUBLE_PAGES');
    expect(spreadIndexOf(spreads, 3)).toBe(1);
    expect(spreadIndexOf(spreads, 99)).toBe(0);
    expect(highestPage(spreads[2])).toBe(5);
  });
});
