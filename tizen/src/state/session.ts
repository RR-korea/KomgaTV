// 서버 세션: 저장된 자격 증명 → KomgaClient + 이미지 캐시. Android의 ServerSessionManager 역할.
// 저장은 앱 전용 localStorage (PLAN Z5). 비밀번호는 평문이므로 스토어 전에 KeyManager로 바꾼다.
import { type Auth, connect, KomgaClient } from '../api/client';
import { ConnectionError, HttpError } from '../api/errors';
import type { KomgaUser } from '../api/types';
import { ImagePaths, normalizeServerUrl } from '../api/url';
import { clearLanczos } from '../image/lanczosPool';
import { extractSingleJpeg } from '../pdf/pdfJpeg';
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

/** 지금 연결 설정 */
const STORAGE_KEY = 'komgatv.server';
/** 연결에 성공한 서버 목록 (Android T11) */
const LIST_KEY = 'komgatv.servers';

function isSavedServer(value: unknown): value is SavedServer {
  if (!value || typeof value !== 'object') return false;
  const server = value as SavedServer;
  if (typeof server.url !== 'string' || !server.auth) return false;
  return server.auth.kind === 'basic'
    ? typeof server.auth.email === 'string' && typeof server.auth.password === 'string'
    : server.auth.kind === 'apiKey' && typeof server.auth.apiKey === 'string';
}

/** 같은 서버·같은 계정이면 같은 항목 (API 키는 서버당 하나) — Android T11과 같음 */
export function serverId(server: SavedServer): string {
  const url = normalizeServerUrl(server.url) ?? server.url.trim();
  return server.auth.kind === 'basic' ? `${url}|${server.auth.email.trim().toLowerCase()}` : `${url}|apikey`;
}

/** 목록에 넣거나(같은 ID면 바꾼다) — 순수 함수 */
export function upsertServer(list: SavedServer[], server: SavedServer): SavedServer[] {
  const id = serverId(server);
  const index = list.findIndex((item) => serverId(item) === id);
  if (index < 0) return [...list, server];
  const next = list.slice();
  next[index] = server;
  return next;
}

export function removeServerFrom(list: SavedServer[], id: string): SavedServer[] {
  return list.filter((item) => serverId(item) !== id);
}

/** 저장된 목록 문자열 → 목록. 깨진 항목은 버린다 — 순수 함수 */
export function parseServerList(raw: string | null): SavedServer[] {
  if (!raw) return [];
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter(isSavedServer) : [];
  } catch {
    return [];
  }
}

export function loadSavedServer(): SavedServer | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    return isSavedServer(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

/** 저장된 서버 목록. 목록이 생기기 전 버전에서 연결한 서버도 들어 있게 한다. */
export function loadServerList(): SavedServer[] {
  let list: SavedServer[];
  try {
    list = parseServerList(localStorage.getItem(LIST_KEY));
  } catch {
    list = [];
  }
  const current = loadSavedServer();
  return current ? upsertServer(list, current) : list;
}

function writeServerList(list: SavedServer[]): void {
  localStorage.setItem(LIST_KEY, JSON.stringify(list));
}

/** 지금 연결로 정하고 목록에도 넣는다 */
export function saveServer(server: SavedServer): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(server));
  writeServerList(upsertServer(loadServerList(), server));
}

export function removeSavedServer(id: string): SavedServer[] {
  const next = removeServerFrom(loadServerList(), id);
  writeServerList(next);
  return next;
}

/** 저장된 다른 서버로 바꾼다: 세션을 새 서버로 만들고(옛 서버 이미지 캐시는 비움) 지금 연결로 저장 */
export function switchServer(server: SavedServer): boolean {
  if (!restoreSession(server)) return false;
  saveServer(server);
  return true;
}

export const session = new Store<Session | null>(null);

/**
 * 리더 쪽 그림 받기. "pdf:책:쪽"이면 원본 PDF를 받아 JPEG를 꺼내고(0.2초), 모양이 맞지 않으면 Komga가 그린 그림(쪽당 2~6초).
 */
export async function loadPage(client: KomgaClient, key: string, signal: AbortSignal): Promise<Blob> {
  if (!key.startsWith('pdf:')) return client.fetchImage(key, signal);
  const [, bookId, page] = key.split(':');
  let raw: Uint8Array | null = null;
  try {
    raw = await client.fetchRawPage(bookId, Number(page), signal);
  } catch (error) {
    // 서버가 원본 쪽을 못 주면(파일을 옮겼거나 분석 중 등) 서버가 그린 일반 쪽으로 (Android 1.0.1). 취소는 그대로
    if (signal.aborted) throw error;
  }
  const jpeg = raw ? extractSingleJpeg(raw) : null;
  if (jpeg) return new Blob([jpeg.slice()], { type: 'image/jpeg' });
  return client.fetchImage(ImagePaths.bookPage(bookId, Number(page)), signal);
}

function setSession(client: KomgaClient, user: KomgaUser | null): Session {
  const previous = session.get();
  previous?.thumbnails.clear();
  previous?.pages.clear();
  clearLanczos();
  const next: Session = {
    client,
    user,
    thumbnails: new ImageCache((path, signal) => client.fetchImage(path, signal), 60, undefined, undefined, 6),
    pages: new ImageCache((key, signal) => loadPage(client, key, signal), 12, undefined, undefined, 3),
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
