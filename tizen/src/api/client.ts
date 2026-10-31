// Komga REST 클라이언트. 엔드포인트와 JSON은 Android가 쓰는 komga-client 0.11.0과 같다 (PLAN Z2).
import type { SearchBody } from './conditions';
import { classifyError, ConnectionError, HttpError } from './errors';
import type { KomgaBook, KomgaBookPage, KomgaLibrary, KomgaSeries, KomgaUser, Page, PageRequest } from './types';
import { apiUrl, normalizeServerUrl } from './url';

export type Auth =
  | { kind: 'basic'; email: string; password: string }
  | { kind: 'apiKey'; apiKey: string };

export type FetchFn = (input: string, init?: RequestInit) => Promise<Response>;

const DEFAULT_TIMEOUT_MS = 30_000;

/** btoa는 Latin-1만 받으므로 UTF-8로 먼저 바꾼다 (한글 비밀번호). */
function base64Utf8(text: string): string {
  const bytes = new TextEncoder().encode(text);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary);
}

export function authHeaders(auth: Auth): Record<string, string> {
  return auth.kind === 'basic'
    ? { Authorization: `Basic ${base64Utf8(`${auth.email}:${auth.password}`)}` }
    : { 'X-API-Key': auth.apiKey };
}

export class KomgaClient {
  readonly baseUrl: string;
  private readonly headers: Record<string, string>;

  constructor(
    baseUrl: string,
    auth: Auth,
    private readonly fetchFn: FetchFn = (input, init) => fetch(input, init),
    private readonly timeoutMs = DEFAULT_TIMEOUT_MS,
  ) {
    this.baseUrl = baseUrl;
    this.headers = authHeaders(auth);
  }

  /** 인증을 붙여 요청하고, 2xx가 아니면 HttpError. */
  async request(path: string, init: RequestInit & { query?: PageRequest } = {}): Promise<Response> {
    const { query, ...rest } = init;
    const url = apiUrl(this.baseUrl, path, query && { page: query.page, size: query.size, sort: query.sort });
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await this.fetchFn(url, {
        ...rest,
        headers: { ...this.headers, ...(rest.headers as Record<string, string> | undefined) },
        signal: controller.signal,
      });
      if (!response.ok) throw new HttpError(response.status, url);
      return response;
    } finally {
      clearTimeout(timer);
    }
  }

  private async json<T>(path: string, init?: RequestInit & { query?: PageRequest }): Promise<T> {
    return (await this.request(path, init)).json() as Promise<T>;
  }

  private postJson<T>(path: string, body: unknown, query?: PageRequest): Promise<T> {
    return this.json<T>(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      query,
    });
  }

  getMe(): Promise<KomgaUser> {
    return this.json('api/v2/users/me');
  }

  getLibraries(): Promise<KomgaLibrary[]> {
    return this.json('api/v1/libraries');
  }

  getSeriesList(search: SearchBody, page?: PageRequest): Promise<Page<KomgaSeries>> {
    return this.postJson('api/v1/series/list', search, page);
  }

  getSeries(seriesId: string): Promise<KomgaSeries> {
    return this.json(`api/v1/series/${encodeURIComponent(seriesId)}`);
  }

  getBookList(search: SearchBody, page?: PageRequest): Promise<Page<KomgaBook>> {
    return this.postJson('api/v1/books/list', search, page);
  }

  // 홈 줄 (Komelia 홈과 같음, Android T9)
  getBooksOnDeck(page?: PageRequest): Promise<Page<KomgaBook>> {
    return this.json('api/v1/books/ondeck', { query: page });
  }

  getLatestBooks(page?: PageRequest): Promise<Page<KomgaBook>> {
    return this.json('api/v1/books/latest', { query: page });
  }

  getNewSeries(page?: PageRequest): Promise<Page<KomgaSeries>> {
    return this.json('api/v1/series/new', { query: page });
  }

  getUpdatedSeries(page?: PageRequest): Promise<Page<KomgaSeries>> {
    return this.json('api/v1/series/updated', { query: page });
  }

  getBook(bookId: string): Promise<KomgaBook> {
    return this.json(`api/v1/books/${encodeURIComponent(bookId)}`);
  }

  getBookPages(bookId: string): Promise<KomgaBookPage[]> {
    return this.json(`api/v1/books/${encodeURIComponent(bookId)}/pages`);
  }

  /** page는 1부터. 성공하면 204. */
  async markReadProgress(bookId: string, page: number, completed: boolean): Promise<void> {
    await this.request(`api/v1/books/${encodeURIComponent(bookId)}/read-progress`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ page, completed }),
    });
  }

  /** 인증 이미지 (표지·페이지). path는 ImagePaths로 만든다. */
  async fetchImage(path: string): Promise<Blob> {
    return (await this.request(path, { headers: { Accept: 'image/*' } })).blob();
  }
}

/**
 * 서버 주소·인증을 확인하고 클라이언트를 돌려준다. 실패하면 ConnectionError.
 * 저장은 호출한 쪽이 한다.
 */
export async function connect(
  serverUrl: string,
  auth: Auth,
  fetchFn?: FetchFn,
  timeoutMs?: number,
): Promise<{ client: KomgaClient; user: KomgaUser }> {
  const baseUrl = normalizeServerUrl(serverUrl);
  if (!baseUrl) {
    throw new ConnectionError('invalidUrl', {
      message: serverUrl.trim() ? undefined : '서버 주소를 입력해 주세요.',
    });
  }
  const client = new KomgaClient(baseUrl, auth, fetchFn, timeoutMs);
  try {
    const user = await client.getMe();
    return { client, user };
  } catch (error) {
    throw classifyError(error);
  }
}
