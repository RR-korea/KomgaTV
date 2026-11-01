// Komga API 응답 중 앱이 쓰는 필드만. 이름은 Komga JSON 그대로 (komga-client 0.11.0 모델 기준).

export type ReadingDirection = 'LEFT_TO_RIGHT' | 'RIGHT_TO_LEFT' | 'VERTICAL' | 'WEBTOON';
export type ReadStatus = 'UNREAD' | 'READ' | 'IN_PROGRESS';

export interface KomgaUser {
  id: string;
  email: string;
}

export interface KomgaLibrary {
  id: string;
  name: string;
}

export interface KomgaSeries {
  id: string;
  libraryId: string;
  name: string;
  booksCount: number;
  booksReadCount: number;
  booksUnreadCount: number;
  booksInProgressCount: number;
  metadata: {
    title: string;
    summary: string;
    /** ENDED | ONGOING | ABANDONED | HIATUS */
    status: string;
    publisher: string;
    readingDirection: ReadingDirection | null;
  };
}

export interface KomgaReadProgress {
  page: number;
  completed: boolean;
}

export interface KomgaBook {
  id: string;
  seriesId: string;
  seriesTitle: string;
  name: string;
  number: number;
  media: {
    pagesCount: number;
    /** DIVINA(이미지) / PDF / EPUB — EPUB이면 글 리더로 연다 (Android T12) */
    mediaProfile?: string;
  };
  metadata: {
    title: string;
    number: string;
  };
  readProgress: KomgaReadProgress | null;
}

export interface KomgaBookPage {
  number: number;
  width: number | null;
  height: number | null;
  mediaType: string;
}

/** Spring Page. number는 0부터. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  last: boolean;
}

export interface PageRequest {
  page?: number;
  size?: number;
  /** "metadata.titleSort,asc" 형식 */
  sort?: string;
}

export const SeriesSort = {
  titleAsc: 'metadata.titleSort,asc',
} as const;

export const BookSort = {
  numberAsc: 'metadata.numberSort,asc',
  readDateDesc: 'readProgress.readDate,desc',
} as const;

// EPUB (Readium WebPub manifest·progression, Android T12와 같은 엔드포인트)
export interface WebPubLink {
  href: string;
  type?: string;
  title?: string;
  /** 목차의 하위 항목 */
  children?: WebPubLink[];
}

export interface WebPubManifest {
  readingOrder: WebPubLink[];
  toc?: WebPubLink[];
}

export interface R2Locator {
  /** 책 안 경로 (예: index_split_001.html) */
  href: string;
  type: string;
  locations?: { progression?: number; totalProgression?: number };
}

export interface R2Progression {
  modified: string;
  device: { id: string; name: string };
  locator: R2Locator;
}
