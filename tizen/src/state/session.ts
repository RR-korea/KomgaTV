// 서버 세션: 저장된 자격 증명 → KomgaClient + 이미지 캐시. Android의 ServerSessionManager 역할.
// 저장은 앱 전용 localStorage (PLAN Z5). 비밀번호는 평문이므로 스토어 전에 KeyManager로 바꾼다.
import { type Auth, connect, KomgaClient } from '../api/client';
import { ConnectionError, HttpError } from '../api/errors';
import type { KomgaUser } from '../api/types';
import { normalizeServerUrl } from '../api/url';
import { ImageCache } from '../image/ImageCache';
import { Store } from './store';

export interface SavedServer {
  url: string;
  auth: Auth;
}

export interface Session {
  client: KomgaClient;
  /** 연결 확인을 거쳤으면 사용자, 저장된 값으로 복원만 했으면 null */
  user: KomgaUser | null;
  /** 표지 등 작은 이미지 — 여유 있게 보관 */
  thumbnails: ImageCache;
  /** 리더 페이지 — 큰 이미지라 적게 보관 (현재 펼침 + 앞뒤 2펼침 프리로드가 들어가는 정도) */
  pages: ImageCache;
}

const STORAGE_KEY = 'komgatv.server';

export function loadSavedServer(): SavedServer | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as SavedServer;
    if (typeof parsed.url !== 'string' || !parsed.auth) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function saveServer(server: SavedServer): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(server));
}

export const session = new Store<Session | null>(null);

function setSession(client: KomgaClient, user: KomgaUser | null): Session {
  const previous = session.get();
  previous?.thumbnails.clear();
  previous?.pages.clear();
  const next: Session = {
    client,
    user,
    thumbnails: new ImageCache((path) => client.fetchImage(path), 60),
    pages: new ImageCache((path) => client.fetchImage(path), 10),
  };
  session.set(next);
  return next;
}

/** 연결을 확인하고 세션을 연다. 실패하면 ConnectionError. 저장은 하지 않는다. */
export async function openSession(server: SavedServer): Promise<Session> {
  const { client, user } = await connect(server.url, server.auth);
  return setSession(client, user);
}

/** 앱 시작: 저장된 값으로 네트워크 없이 세션을 만든다 (서버가 꺼져 있어도 홈에서 다시 시도할 수 있게). */
export function restoreSession(server: SavedServer): Session | null {
  const baseUrl = normalizeServerUrl(server.url);
  if (!baseUrl) return null;
  return setSession(new KomgaClient(baseUrl, server.auth), null);
}

export class NotConnectedError extends Error {
  constructor() {
    super('Not connected');
    this.name = 'NotConnectedError';
  }
}

/** 화면에서 쓰는 현재 세션. 없으면 NotConnectedError. */
export function requireSession(): Session {
  const current = session.get();
  if (!current) throw new NotConnectedError();
  return current;
}

// 진행 저장(PATCH)과 화면 새로고침의 경쟁 조건(E2E-1 남은 문제) — 새로고침은 진행 중인 쓰기를 기다린다.
let pendingWrites: Promise<unknown> = Promise.resolve();

export function trackWrite<T>(write: Promise<T>): Promise<T> {
  pendingWrites = Promise.all([pendingWrites, write.catch(() => undefined)]);
  return write;
}

export function awaitWrites(): Promise<unknown> {
  return pendingWrites;
}

export type LoadError = 'notConnected' | 'unreachable' | 'unauthorized' | 'unknown';

/** 화면이 보여 줄 오류 종류로 바꾼다 (Android의 toLoadError). */
export function toLoadError(error: unknown): LoadError {
  if (error instanceof NotConnectedError) return 'notConnected';
  if (error instanceof ConnectionError) {
    if (error.kind === 'unauthorized') return 'unauthorized';
    if (error.kind === 'unreachable') return 'unreachable';
    return 'unknown';
  }
  if (error instanceof HttpError) return error.status === 401 ? 'unauthorized' : 'unknown';
  if (error instanceof TypeError || (error instanceof Error && error.name === 'AbortError')) return 'unreachable';
  return 'unknown';
}
