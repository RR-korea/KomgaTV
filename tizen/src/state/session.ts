// 서버 세션: 저장된 자격 증명 → KomgaClient + 이미지 캐시. Android의 ServerSessionManager 역할.
// 저장은 앱 전용 localStorage (PLAN Z5). 비밀번호는 평문이므로 스토어 전에 KeyManager로 바꾼다.
import { type Auth, connect, KomgaClient } from '../api/client';
import { ConnectionError } from '../api/errors';
import type { KomgaUser } from '../api/types';
import { ImageCache } from '../image/ImageCache';
import { Store } from './store';

export interface SavedServer {
  url: string;
  auth: Auth;
}

export interface Session {
  client: KomgaClient;
  user: KomgaUser;
  /** 표지 등 작은 이미지 — 여유 있게 보관 */
  thumbnails: ImageCache;
  /** 리더 페이지 — 큰 이미지라 적게 보관 */
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

function createSession(client: KomgaClient, user: KomgaUser): Session {
  return {
    client,
    user,
    thumbnails: new ImageCache((path) => client.fetchImage(path), 60),
    pages: new ImageCache((path) => client.fetchImage(path), 6),
  };
}

/** 연결을 확인하고 세션을 연다. 실패하면 ConnectionError. 저장은 하지 않는다. */
export async function openSession(server: SavedServer): Promise<Session> {
  const { client, user } = await connect(server.url, server.auth);
  const previous = session.get();
  previous?.thumbnails.clear();
  previous?.pages.clear();
  const next = createSession(client, user);
  session.set(next);
  return next;
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

export type LoadError = 'notConnected' | 'unreachable' | 'unauthorized' | 'unknown';

/** 화면이 보여 줄 오류 종류로 바꾼다 (Android의 toLoadError). */
export function toLoadError(error: unknown): LoadError {
  if (error instanceof NotConnectedError) return 'notConnected';
  if (error instanceof ConnectionError) {
    if (error.kind === 'unauthorized') return 'unauthorized';
    if (error.kind === 'unreachable') return 'unreachable';
    return 'unknown';
  }
  if (error && typeof error === 'object' && 'status' in error && (error as { status: number }).status === 401) {
    return 'unauthorized';
  }
  if (error instanceof TypeError || (error instanceof Error && error.name === 'AbortError')) return 'unreachable';
  return 'unknown';
}
