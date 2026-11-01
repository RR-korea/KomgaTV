// UI 부품 (PLAN Z3). 포커스 대상은 전부 `.focusable` + data-key — platform/focus가 방향키로 옮긴다.
import type { ComponentChildren, RefObject } from 'preact';
import { useEffect, useRef, useState } from 'preact/hooks';
import type { ImageCache } from '../image/ImageCache';
import { FOCUSABLE_CLASS, focusElement, move } from '../platform/focus';
import { isTizen } from '../platform/keys';
import { useLongPress } from '../platform/input';
import { fmt, t } from '../i18n';
import type { LoadError } from '../state/session';
import { session as sessionStore } from '../state/session';
import { useStore } from '../state/store';

interface FocusableProps {
  focusKey: string;
  onClick?: () => void;
  class?: string;
  selected?: boolean;
  children?: ComponentChildren;
}

export function Button({ focusKey, onClick, class: className = '', selected, children }: FocusableProps) {
  return (
    <div
      class={`${FOCUSABLE_CLASS} button${selected ? ' selected' : ''} ${className}`}
      tabIndex={-1}
      data-key={focusKey}
      onClick={onClick}
    >
      {children}
    </div>
  );
}

interface TextFieldProps {
  focusKey: string;
  label: string;
  value: string;
  onInput: (value: string) => void;
  type?: 'text' | 'password' | 'url' | 'email';
  placeholder?: string;
}

// Tizen IME 키: 완료 65376, 취소 65385
const IME_DONE = 65376;
const IME_CANCEL = 65385;

/**
 * 입력칸. 지나가기만 해서는 키보드가 뜨지 않고 OK로 입력을 시작한다 (Android E2E-1 1번 규칙).
 * - Tizen: 화면 키보드(IME)가 떠 있는 동안 방향키·OK가 페이지에도 전달된다 (TV 에뮬레이터에서 확인).
 *   그래서 IME의 완료·취소와 Back으로만 입력을 끝낸다 — 위/아래로 끝내면 키보드 안에서 움직일 수 없다.
 * - PC(개발용): 위/아래는 입력을 끝내고 이동, Enter·Esc는 입력만 끝낸다.
 */
export function TextField({ focusKey, label, value, onInput, type = 'text', placeholder }: TextFieldProps) {
  const boxRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [editing, setEditing] = useState(false);

  const stopEditing = () => {
    inputRef.current?.blur();
    setEditing(false);
    if (boxRef.current) focusElement(boxRef.current);
  };

  const onKeyDown = (event: KeyboardEvent) => {
    if (isTizen()) {
      if (event.keyCode === IME_DONE || event.keyCode === IME_CANCEL || event.keyCode === 10009) {
        event.preventDefault();
        stopEditing();
      }
      return;
    }
    switch (event.keyCode) {
      case 38:
      case 40:
        event.preventDefault();
        stopEditing();
        move(event.keyCode === 38 ? 'up' : 'down');
        break;
      case 13:
      case IME_DONE:
      case IME_CANCEL:
      case 10009:
      case 27:
        event.preventDefault();
        stopEditing();
        break;
      default:
        break;
    }
  };

  return (
    <div
      ref={boxRef}
      class={`${FOCUSABLE_CLASS} field${editing ? ' editing' : ''}`}
      tabIndex={-1}
      data-key={focusKey}
      onClick={() => {
        setEditing(true);
        inputRef.current?.focus();
      }}
    >
      <div class="field-label">{label}</div>
      <input
        ref={inputRef}
        class="field-input"
        type={type as 'text'}
        value={value}
        placeholder={placeholder}
        tabIndex={-1}
        autoComplete="off"
        spellcheck={false}
        onInput={(event) => onInput((event.target as HTMLInputElement).value)}
        onKeyDown={onKeyDown}
        onBlur={() => setEditing(false)}
      />
    </div>
  );
}

// 화면 근처에 온 카드만 이미지를 받는다. 하나의 관찰자를 같이 쓴다.
type VisibilityListener = (visible: boolean) => void;
const visibilityListeners = new Map<Element, VisibilityListener>();
let observer: IntersectionObserver | null = null;

function observe(element: Element, listener: VisibilityListener): () => void {
  if (!observer) {
    observer = new IntersectionObserver(
      (entries) => entries.forEach((entry) => visibilityListeners.get(entry.target)?.(entry.isIntersecting)),
      { rootMargin: '540px 960px' },
    );
  }
  visibilityListeners.set(element, listener);
  observer.observe(element);
  return () => {
    visibilityListeners.delete(element);
    observer?.unobserve(element);
  };
}

export function useInView(ref: RefObject<Element | null>): boolean {
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    const element = ref.current;
    if (!element) return undefined;
    return observe(element, setVisible);
  }, [ref]);
  return visible;
}

export interface CachedImage {
  url: string | null;
  failed: boolean;
}

const NO_IMAGE: CachedImage = { url: null, failed: false };

/**
 * 캐시에서 object URL을 받아 쓰고, 화면에서 빠지면 놓는다.
 * 놓은 URL은 캐시가 언제든 해제(revoke)할 수 있으므로, 다시 받기 전까지는 옛 URL을 절대 돌려주지 않는다
 * (화면 밖으로 나갔다 돌아온 카드가 해제된 blob을 그리려다 ERR_FILE_NOT_FOUND가 나던 문제).
 */
export function useCachedImage(cache: ImageCache | undefined, path: string | null, enabled = true): CachedImage {
  const [state, setState] = useState<{ owner: object | null; image: CachedImage }>({ owner: null, image: NO_IMAGE });
  const holder = useRef<object | null>(null);
  useEffect(() => {
    if (!cache || !path || !enabled) return undefined;
    const owner = {};
    holder.current = owner;
    let acquired = false;
    cache.acquire(path).then(
      (url) => {
        if (holder.current === owner) {
          acquired = true;
          setState({ owner, image: { url, failed: false } });
        } else {
          cache.release(path);
        }
      },
      () => {
        if (holder.current === owner) setState({ owner, image: { url: null, failed: true } });
      },
    );
    return () => {
      if (holder.current === owner) holder.current = null;
      if (acquired) cache.release(path);
    };
  }, [cache, path, enabled]);
  // 지금 붙잡고 있는 요청의 결과일 때만 보여 준다
  return enabled && state.owner !== null && state.owner === holder.current ? state.image : NO_IMAGE;
}

interface PosterCardProps {
  focusKey: string;
  imagePath: string;
  title: string;
  subtitle?: string;
  /** 0~1, null이면 진행 막대 없음 */
  progress?: number | null;
  onClick: () => void;
  /** OK 길게 누르기 (메뉴) */
  onLongPress?: () => void;
  index?: number;
}

export function PosterCard({ focusKey, imagePath, title, subtitle, progress, onClick, onLongPress, index }: PosterCardProps) {
  const ref = useRef<HTMLDivElement>(null);
  useLongPress(ref, onLongPress);
  const visible = useInView(ref);
  const current = useStore(sessionStore);
  const image = useCachedImage(current?.thumbnails, imagePath, visible);
  return (
    <div
      ref={ref}
      class={`${FOCUSABLE_CLASS} card`}
      tabIndex={-1}
      data-key={focusKey}
      data-index={index}
      onClick={onClick}
    >
      <div class="card-poster">
        {image.url ? (
          <img class="card-image" src={image.url} alt="" />
        ) : (
          <div class="card-placeholder">{image.failed ? title.charAt(0) : ''}</div>
        )}
        {progress != null && (
          <div class="card-progress">
            <div class="card-progress-fill" style={{ width: `${Math.round(progress * 100)}%` }} />
          </div>
        )}
      </div>
      <div class="card-title">{title}</div>
      {subtitle && <div class="card-subtitle">{subtitle}</div>}
    </div>
  );
}

/** 그림 없는 카드 (라이브러리 — Komga에 라이브러리 표지가 없다, E2E-1). 이름을 카드 안에 넣는다. */
export function TextCard({
  focusKey,
  title,
  onClick,
  onLongPress,
}: {
  focusKey: string;
  title: string;
  onClick: () => void;
  onLongPress?: () => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useLongPress(ref, onLongPress);
  return (
    <div ref={ref} class={`${FOCUSABLE_CLASS} text-card`} tabIndex={-1} data-key={focusKey} onClick={onClick}>
      <div class="text-card-title">{title}</div>
    </div>
  );
}

export function Row({ section, title, children }: { section: string; title: string; children: ComponentChildren }) {
  return (
    <section class="row">
      <h2 class="row-title">{title}</h2>
      <div class="row-track" data-scroll="x" data-scroll-margin="96" data-section={section}>
        <div class="row-items">{children}</div>
      </div>
    </section>
  );
}

export function ProgressBar({ value, rtl = false }: { value: number; rtl?: boolean }) {
  return (
    <div class={`progress${rtl ? ' rtl' : ''}`}>
      <div class="progress-fill" style={{ width: `${Math.max(0, Math.min(1, value)) * 100}%` }} />
    </div>
  );
}

export function LoadingView({ text = t.loading }: { text?: string }) {
  return <div class="center-message">{text}</div>;
}

export function loadErrorText(error: LoadError): string {
  switch (error) {
    case 'notConnected':
      return t.errorNotConnected;
    case 'unreachable':
      return t.errorUnreachable;
    case 'unauthorized':
      return t.errorUnauthorized;
    default:
      return t.errorUnknown;
  }
}

export function ErrorView({ error, onRetry }: { error: LoadError; onRetry: () => void }) {
  return (
    <div class="center-message">
      <div class="error-title">{t.errorTitle}</div>
      <div class="error-text">{loadErrorText(error)}</div>
      <Button focusKey="retry" onClick={onRetry}>
        {t.retry}
      </Button>
    </div>
  );
}

export { fmt };
