// EPUB 리더 (Android T12와 같은 동작): 장 하나씩, 어두운 화면·밝은 글자·가운데 46em 폭·줄 간격 1.8.
// 리모컨: 위/아래 = 화면 90%씩(장 끝에서 아래 = 다음 장, 장 처음에서 위 = 이전 장 끝), 좌/우 = 이전/다음 장,
// OK = 정보(제목, "16장 중 2장", 전체 진행, 위/아래 = 글자 크기 60~200%), Back = 정보 닫기 → 나가기.
// 읽은 자리(장 + 장 안 위치)는 Komga Readium progression으로 2초 디바운스 + 나갈 때 즉시 저장 — Komga가 읽기 진행으로 바꾼다.
import { useEffect, useRef, useState } from 'preact/hooks';
import { bookDisplayTitle } from '../api/display';
import type { KomgaBook } from '../api/types';
import {
  type Chapter,
  chaptersOf,
  currentTocIndex,
  type TocEntry,
  tocEntries,
  EPUB_SCROLL_RATIO,
  EpubPositionSaver,
  type EpubPosition,
  progressionOf,
  startPosition,
  stepZoom,
  toProgression,
  totalProgression,
} from '../epub/epub';
import { prepareChapter } from '../epub/chapter';
import { fmt, t } from '../i18n';
import { useKeys } from '../platform/input';
import { current, focusKeyOrFirst, move } from '../platform/focus';
import type { AppKey } from '../platform/keys';
import { pop } from '../platform/router';
import { readerPrefs, saveReaderPrefs } from '../state/readerPrefs';
import { type LoadError, requireSession, toLoadError, trackWrite } from '../state/session';
import { useStore } from '../state/store';
import { ErrorView, LoadingView, ProgressBar } from '../ui/components';

/** 장을 그린 뒤 어디로 스크롤할지: 장 안 위치(0~1) 또는 장 끝 */
type ScrollTarget = number | 'end';

export function EpubReader({ book, active }: { book: KomgaBook; active: boolean }) {
  const prefs = useStore(readerPrefs);
  const [chapters, setChapters] = useState<Chapter[]>([]);
  const [position, setPosition] = useState<EpubPosition>({ chapter: 0, progression: 0 });
  const [html, setHtml] = useState<string | null>(null);
  const [error, setError] = useState<LoadError | null>(null);
  const [overlay, setOverlay] = useState(false);
  /** 목차 (Android T13): 정보가 열린 상태에서 OK */
  const [toc, setToc] = useState<TocEntry[]>([]);
  const [tocOpen, setTocOpen] = useState(false);
  const tocPanel = useRef<HTMLDivElement>(null);
  const viewport = useRef<HTMLDivElement>(null);
  const content = useRef<HTMLDivElement>(null);
  const pendingScroll = useRef<ScrollTarget>(0);
  const images = useRef<{ urls: string[]; objectUrls: string[] }>({ urls: [], objectUrls: [] });
  const saver = useRef<EpubPositionSaver | null>(null);
  const chaptersRef = useRef<Chapter[]>([]);
  const loadSeq = useRef(0);
  const exiting = useRef(false);

  const releaseImages = () => {
    images.current.objectUrls.forEach((url) => URL.revokeObjectURL(url));
    images.current = { urls: [], objectUrls: [] };
  };

  const loadChapter = (list: Chapter[], index: number, scroll: ScrollTarget) => {
    const chapter = list[index];
    if (!chapter) return;
    const seq = ++loadSeq.current;
    setHtml(null);
    requireSession()
      .client.fetchText(chapter.href)
      .then(
        (source) => {
          if (seq !== loadSeq.current) return;
          const prepared = prepareChapter(source, chapter.href);
          releaseImages();
          images.current.urls = prepared.images;
          pendingScroll.current = scroll;
          setPosition({ chapter: index, progression: scroll === 'end' ? 1 : scroll });
          setHtml(prepared.html);
        },
        (failure: unknown) => {
          if (seq === loadSeq.current) setError(toLoadError(failure));
        },
      );
  };

  const load = () => {
    setError(null);
    const { client } = requireSession();
    Promise.all([client.getWebPubManifest(book.id), client.getProgression(book.id).catch(() => null)]).then(
      ([manifest, progression]) => {
        const list = chaptersOf(manifest);
        chaptersRef.current = list;
        setChapters(list);
        setToc(tocEntries(manifest, list, (n) => fmt(t.epubChapterShort, n)));
        saver.current = new EpubPositionSaver((pos) =>
          trackWrite(requireSession().client.putProgression(book.id, toProgression(chaptersRef.current, pos, new Date()))),
        );
        const start = startPosition(list, book.readProgress, progression);
        loadChapter(list, start.chapter, start.progression);
      },
      (failure: unknown) => setError(toLoadError(failure)),
    );
  };

  useEffect(load, []);
  useEffect(
    () => () => {
      void saver.current?.flush();
      releaseImages();
    },
    [],
  );

  // 장을 그린 뒤: 그림을 인증 fetch로 채우고, 정한 위치로 스크롤
  useEffect(() => {
    const view = viewport.current;
    const body = content.current;
    if (html === null || !view || !body) return;
    const target = pendingScroll.current;
    const max = () => Math.max(0, view.scrollHeight - view.clientHeight);
    view.scrollTop = target === 'end' ? max() : Math.round(target * max());
    const { client } = requireSession();
    const urls = images.current.urls;
    Array.from(body.querySelectorAll<HTMLElement>('[data-epub-img]')).forEach((element) => {
      const url = urls[Number(element.getAttribute('data-epub-img'))];
      if (!url) return;
      client.fetchBlob(url).then(
        (blob) => {
          if (images.current.urls !== urls) return; // 그사이 장이 바뀌었다
          const objectUrl = URL.createObjectURL(blob);
          images.current.objectUrls.push(objectUrl);
          if (element.localName.toLowerCase() === 'image') element.setAttribute('href', objectUrl);
          else element.setAttribute('src', objectUrl);
        },
        () => undefined,
      );
    });
  }, [html]);

  const totalChapters = chapters.length;

  const goChapter = (index: number, scroll: ScrollTarget) => {
    if (index < 0 || index >= totalChapters) return;
    loadChapter(chapters, index, scroll);
    saver.current?.schedule({ chapter: index, progression: scroll === 'end' ? 1 : 0 });
  };

  const scrollPage = (direction: 1 | -1) => {
    const view = viewport.current;
    if (!view || html === null) return;
    const max = Math.max(0, view.scrollHeight - view.clientHeight);
    if (direction > 0 && view.scrollTop >= max - 2) {
      goChapter(position.chapter + 1, 0);
      return;
    }
    if (direction < 0 && view.scrollTop <= 2) {
      goChapter(position.chapter - 1, 'end');
      return;
    }
    const target = Math.min(max, Math.max(0, view.scrollTop + direction * view.clientHeight * EPUB_SCROLL_RATIO));
    view.scrollTo({ top: target, behavior: 'smooth' });
    const next = { chapter: position.chapter, progression: progressionOf(target, view.scrollHeight, view.clientHeight) };
    setPosition(next);
    saver.current?.schedule(next);
  };

  const exit = () => {
    if (exiting.current) return;
    exiting.current = true;
    void saver.current?.flush();
    pop();
  };

  // 목차가 열리면 지금 장의 줄에 포커스
  useEffect(() => {
    if (tocOpen && tocPanel.current) focusKeyOrFirst(tocPanel.current, `toc-${currentTocIndex(toc, position.chapter)}`);
  }, [tocOpen]);

  const onKey = (key: AppKey): boolean => {
    if (error || !totalChapters) {
      if (key === 'back') {
        exit();
        return true;
      }
      return false; // 오류 화면의 다시 시도 버튼은 기본 처리로
    }
    if (tocOpen) {
      switch (key) {
        case 'up':
        case 'down':
          move(key);
          break;
        case 'enter': {
          const index = Number(current()?.dataset.key?.replace('toc-', ''));
          const entry = toc[index];
          setTocOpen(false);
          setOverlay(false);
          if (entry) goChapter(entry.chapter, 0);
          break;
        }
        case 'back':
          setTocOpen(false); // 정보로
          break;
        default:
          break;
      }
      return true;
    }
    switch (key) {
      case 'up':
        if (overlay) saveReaderPrefs({ epubTextZoom: stepZoom(prefs.epubTextZoom, 1) });
        else scrollPage(-1);
        break;
      case 'down':
        if (overlay) saveReaderPrefs({ epubTextZoom: stepZoom(prefs.epubTextZoom, -1) });
        else scrollPage(1);
        break;
      case 'left':
        goChapter(position.chapter - 1, 0);
        break;
      case 'right':
        goChapter(position.chapter + 1, 0);
        break;
      case 'enter':
        if (overlay) setTocOpen(true);
        else setOverlay(true);
        break;
      case 'back':
        if (overlay) setOverlay(false);
        else exit();
        break;
      default:
        break;
    }
    return true;
  };

  useKeys(onKey, active);

  if (error) {
    return (
      <div class="reader epub">
        <ErrorView error={error} onRetry={load} />
      </div>
    );
  }

  const chapterTitle = chapters[position.chapter]?.title;
  const total = totalProgression(position, totalChapters);

  return (
    <div class="reader epub">
      <div ref={viewport} class="epub-viewport">
        {html === null ? (
          <LoadingView text={t.readerLoading} />
        ) : (
          <div
            ref={content}
            class="epub-content"
            style={{ fontSize: `${Math.round((32 * prefs.epubTextZoom) / 100)}px` }}
            dangerouslySetInnerHTML={{ __html: html }}
          />
        )}
      </div>
      {tocOpen && (
        <div class="reader-panel-backdrop">
          <div ref={tocPanel} class="reader-panel toc-panel" data-focus-trap data-scroll="y" data-scroll-margin="120">
            <h2 class="reader-panel-title">{t.epubTocTitle}</h2>
            {toc.map((entry, i) => (
              <div
                key={`${entry.chapter}-${i}`}
                class={`focusable toc-entry${entry.chapter === position.chapter ? ' current' : ''}`}
                style={{ paddingLeft: `${24 + entry.depth * 32}px` }}
                tabIndex={-1}
                data-key={`toc-${i}`}
              >
                {entry.title}
              </div>
            ))}
          </div>
        </div>
      )}
      {overlay && !tocOpen && (
        <div class="reader-overlay">
          <div class="reader-overlay-bar">
            <div class="reader-overlay-row">
              <div class="reader-title">{bookDisplayTitle(book)}</div>
            </div>
            <div class="reader-overlay-row">
              <div>
                {fmt(t.epubChapter, position.chapter + 1, totalChapters)}
                {chapterTitle ? ` · ${chapterTitle}` : ''}
              </div>
              <div>{`${Math.round(total * 100)}%`}</div>
            </div>
            <ProgressBar value={total} />
            <div class="reader-help">{fmt(t.epubOverlayHint, prefs.epubTextZoom)}</div>
          </div>
        </div>
      )}
    </div>
  );
}
