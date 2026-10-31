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
