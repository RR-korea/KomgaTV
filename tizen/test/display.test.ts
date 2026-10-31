import { describe, expect, it } from 'vitest';
import { bookDisplayTitle, bookProgress } from '../src/api/display';
import type { KomgaBook } from '../src/api/types';

const book = (title: string, progress: KomgaBook['readProgress'] = null, pages = 100): KomgaBook => ({
  id: 'b',
  seriesId: 's',
  seriesTitle: '킹덤',
  name: title,
  number: 1,
  media: { pagesCount: pages },
  metadata: { title, number: '1' },
  readProgress: progress,
});

describe('bookDisplayTitle', () => {
  it('번호뿐인 이름에는 시리즈 이름을 붙인다', () => {
    expect(bookDisplayTitle(book('01'))).toBe('킹덤 01');
    expect(bookDisplayTitle(book('3.5'))).toBe('킹덤 3.5');
  });

  it('제목이 있으면 그대로', () => {
    expect(bookDisplayTitle(book('킹덤 72권'))).toBe('킹덤 72권');
  });
});

describe('bookProgress', () => {
  it('진행 없음 null, 다 읽음 1, 중간은 비율', () => {
    expect(bookProgress(book('1'))).toBeNull();
    expect(bookProgress(book('1', { page: 50, completed: false }))).toBe(0.5);
    expect(bookProgress(book('1', { page: 3, completed: true }))).toBe(1);
  });
});
