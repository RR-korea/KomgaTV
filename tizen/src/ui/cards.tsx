// 책·시리즈 카드 (홈·검색·시리즈 상세에서 같이 쓴다). 카드 키는 줄 이름을 붙여, 같은 책이 두 줄에 있어도 포커스 복원이 맞게.
import { bookDisplayTitle, bookProgress } from '../api/display';
import type { KomgaBook, KomgaSeries } from '../api/types';
import { ImagePaths } from '../api/url';
import { fmt, t } from '../i18n';
import { push } from '../platform/router';
import { PosterCard } from './components';

export function bookSubtitle(book: KomgaBook): string {
  const progress = book.readProgress;
  if (!progress) return t.seriesUnread;
  if (progress.completed) return t.homeBookCompleted;
  return fmt(t.homeBookProgress, progress.page, book.media.pagesCount);
}

export function BookCard({
  book,
  row,
  index,
  onLongPress,
}: {
  book: KomgaBook;
  row: string;
  index?: number;
  onLongPress?: () => void;
}) {
  return (
    <PosterCard
      focusKey={`${row}-book-${book.id}`}
      index={index}
      imagePath={ImagePaths.bookThumbnail(book.id)}
      title={bookDisplayTitle(book)}
      subtitle={bookSubtitle(book)}
      progress={bookProgress(book)}
      onClick={() => push({ name: 'reader', bookId: book.id })}
      onLongPress={onLongPress}
    />
  );
}

export function SeriesCard({ series, row, index }: { series: KomgaSeries; row: string; index?: number }) {
  return (
    <PosterCard
      focusKey={`${row}-series-${series.id}`}
      index={index}
      imagePath={ImagePaths.seriesThumbnail(series.id)}
      title={series.metadata.title || series.name}
      subtitle={fmt(t.seriesBooksCount, series.booksCount)}
      progress={series.booksCount > 0 && series.booksReadCount > 0 ? series.booksReadCount / series.booksCount : null}
      onClick={() => push({ name: 'series', seriesId: series.id })}
    />
  );
}
