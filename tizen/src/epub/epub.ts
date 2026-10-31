// EPUB 리더 규칙 (Android T12와 같음). 순수 함수 — 테스트한다.
import type { KomgaReadProgress, R2Progression, WebPubManifest } from '../api/types';

export interface Chapter {
  /** manifest의 전체 URL */
  href: string;
  title?: string;
}

export interface EpubPosition {
  /** 0부터인 장 번호 */
  chapter: number;
  /** 그 장 안의 위치 0~1 */
  progression: number;
}

export const EPUB_ZOOM_MIN = 60;
export const EPUB_ZOOM_MAX = 200;
export const EPUB_ZOOM_STEP = 10;
/** 위/아래 한 번에 화면의 90%씩 */
export const EPUB_SCROLL_RATIO = 0.9;

/** 전체 URL → 책 안 경로 (Komga 진행 저장은 이 경로만 받는다: ".../resource/index_split_001.html" → "index_split_001.html") */
export function resourcePath(href: string): string {
  const marker = '/resource/';
  const at = href.indexOf(marker);
  const path = at >= 0 ? href.slice(at + marker.length) : href;
  try {
    return decodeURIComponent(path);
  } catch {
    return path;
  }
}

/** manifest → 장 목록. 목차 제목이 있으면 붙인다. */
export function chaptersOf(manifest: WebPubManifest): Chapter[] {
  const titles = new Map<string, string>();
  for (const entry of manifest.toc ?? []) {
    if (entry.title) titles.set(resourcePath(entry.href.split('#')[0]), entry.title);
  }
  return manifest.readingOrder
    .filter((link) => !!link.href)
    .map((link) => ({ href: link.href, title: link.title ?? titles.get(resourcePath(link.href)) }));
}

/**
 * 시작 위치: 다 읽은 책·읽은 적 없는 책·목록에 없는 장이면 처음부터, 아니면 저장된 장·위치 (Android T12와 같음).
 */
export function startPosition(
  chapters: Chapter[],
  readProgress: KomgaReadProgress | null,
  progression: R2Progression | null,
): EpubPosition {
  if (readProgress?.completed || !progression) return { chapter: 0, progression: 0 };
  const wanted = resourcePath(progression.locator.href);
  const chapter = chapters.findIndex((item) => resourcePath(item.href) === wanted);
  if (chapter < 0) return { chapter: 0, progression: 0 };
  const within = progression.locator.locations?.progression ?? 0;
  return { chapter, progression: Math.min(1, Math.max(0, within)) };
}

/** 책 전체 위치 0~1 (장 수로 나눈 근사) */
export function totalProgression(position: EpubPosition, chapterCount: number): number {
  if (chapterCount <= 0) return 0;
  return Math.min(1, (position.chapter + position.progression) / chapterCount);
}

/** 스크롤 위치 → 장 안 위치 0~1 */
export function progressionOf(scrollTop: number, scrollHeight: number, viewportHeight: number): number {
  const max = scrollHeight - viewportHeight;
  return max <= 0 ? 1 : Math.min(1, Math.max(0, scrollTop / max));
}

export function stepZoom(zoom: number, direction: 1 | -1): number {
  return Math.min(EPUB_ZOOM_MAX, Math.max(EPUB_ZOOM_MIN, zoom + direction * EPUB_ZOOM_STEP));
}

/** 저장할 진행 (Komga Readium progression) */
export function toProgression(chapters: Chapter[], position: EpubPosition, now: Date): R2Progression {
  return {
    modified: now.toISOString(),
    device: { id: 'komgatv-tizen', name: 'KomgaTV (Tizen)' },
    locator: {
      href: resourcePath(chapters[position.chapter]?.href ?? ''),
      type: 'application/xhtml+xml',
      locations: {
        progression: position.progression,
        totalProgression: totalProgression(position, chapters.length),
      },
    },
  };
}

/** 2초 디바운스 + 나갈 때 즉시 저장 (페이지 리더의 ProgressSaver와 같은 규칙) */
export class EpubPositionSaver {
  private timer: ReturnType<typeof setTimeout> | null = null;
  private pending: EpubPosition | null = null;

  constructor(
    private readonly save: (position: EpubPosition) => Promise<void>,
    private readonly delayMs = 2000,
  ) {}

  schedule(position: EpubPosition): void {
    this.pending = position;
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = setTimeout(() => void this.flush(), this.delayMs);
  }

  flush(): Promise<void> {
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = null;
    const next = this.pending;
    this.pending = null;
    return next ? this.save(next).catch(() => undefined) : Promise.resolve();
  }
}
