// 화면 표시용 이름 규칙 (Android E2E-1 9번과 같음).
import type { KomgaBook } from './types';

/**
 * 번호뿐인 책 이름("01", "3.5")에는 시리즈 이름을 붙인다.
 * 제목이 파일 해시(긴 16진수, EPUB에 흔함)면 시리즈 이름을 보인다 (Android T12).
 */
export function bookDisplayTitle(book: KomgaBook): string {
  const title = (book.metadata.title || book.name).trim();
  if (/^[0-9a-f]{16,}$/i.test(title) && book.seriesTitle) return book.seriesTitle;
  return /^\d+(\.\d+)?$/.test(title) && book.seriesTitle ? `${book.seriesTitle} ${title}` : title;
}

/** 0~1 진행률. 다 읽었으면 1, 진행이 없으면 null. */
export function bookProgress(book: KomgaBook): number | null {
  const progress = book.readProgress;
  if (!progress) return null;
  if (progress.completed) return 1;
  const total = book.media.pagesCount;
  return total > 0 ? Math.min(1, progress.page / total) : null;
}
