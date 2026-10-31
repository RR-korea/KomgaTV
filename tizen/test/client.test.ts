import { describe, expect, it } from 'vitest';
import { authHeaders, connect, KomgaClient, type FetchFn } from '../src/api/client';
import { allOf, libraryIs, readStatusIs } from '../src/api/conditions';
import { ConnectionError } from '../src/api/errors';

interface Call {
  url: string;
  init?: RequestInit;
}

/** 요청을 기록하고 정해진 응답을 돌려주는 가짜 fetch */
function fakeFetch(respond: (url: string, init?: RequestInit) => Response | Promise<Response>) {
  const calls: Call[] = [];
  const fetchFn: FetchFn = async (url, init) => {
    calls.push({ url, init });
    return respond(url, init);
  };
  return { calls, fetchFn };
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const user = { id: 'u1', email: 'me@example.com' };

describe('authHeaders', () => {
  it('Basic은 UTF-8 비밀번호도 올바르게 인코딩한다', () => {
    const headers = authHeaders({ kind: 'basic', email: 'a@b.c', password: '비밀' });
    const bytes = Uint8Array.from(atob(headers.Authorization.replace('Basic ', '')), (c) => c.charCodeAt(0));
    const decoded = new TextDecoder().decode(bytes);
    expect(decoded).toBe('a@b.c:비밀');
  });

  it('API 키는 X-API-Key 헤더', () => {
    expect(authHeaders({ kind: 'apiKey', apiKey: 'k' })).toEqual({ 'X-API-Key': 'k' });
  });
});

describe('connect', () => {
  it('성공: 하위 경로 서버의 /api/v2/users/me 를 인증 헤더와 함께 부른다', async () => {
    const { calls, fetchFn } = fakeFetch(() => json(user));
    const result = await connect('https://h/komga/', { kind: 'apiKey', apiKey: 'k' }, fetchFn);
    expect(result.user).toEqual(user);
    expect(result.client.baseUrl).toBe('https://h/komga');
    expect(calls[0].url).toBe('https://h/komga/api/v2/users/me');
    expect((calls[0].init?.headers as Record<string, string>)['X-API-Key']).toBe('k');
  });

  it('401 → unauthorized', async () => {
    const { fetchFn } = fakeFetch(() => json({}, 401));
    await expect(connect('https://h', { kind: 'apiKey', apiKey: 'k' }, fetchFn)).rejects.toMatchObject({
      name: 'ConnectionError',
      kind: 'unauthorized',
      status: 401,
    });
  });

  it('네트워크 실패(TypeError) → unreachable', async () => {
    const { fetchFn } = fakeFetch(() => {
      throw new TypeError('Failed to fetch');
    });
    await expect(connect('https://h', { kind: 'apiKey', apiKey: 'k' }, fetchFn)).rejects.toMatchObject({
      kind: 'unreachable',
    });
  });

  it('시간 초과 → unreachable', async () => {
    const fetchFn: FetchFn = (_url, init) =>
      new Promise((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })));
      });
    await expect(connect('https://h', { kind: 'apiKey', apiKey: 'k' }, fetchFn, 20)).rejects.toMatchObject({
      kind: 'unreachable',
    });
  });

  it('주소 형식 오류 → invalidUrl (fetch 호출 없음)', async () => {
    const { calls, fetchFn } = fakeFetch(() => json(user));
    await expect(connect('not a url', { kind: 'apiKey', apiKey: 'k' }, fetchFn)).rejects.toBeInstanceOf(ConnectionError);
    expect(calls).toHaveLength(0);
  });

  it('그 밖의 HTTP 오류 → other + 상태 코드', async () => {
    const { fetchFn } = fakeFetch(() => json({}, 503));
    await expect(connect('https://h', { kind: 'apiKey', apiKey: 'k' }, fetchFn)).rejects.toMatchObject({
      kind: 'other',
      status: 503,
    });
  });
});

describe('KomgaClient', () => {
  const client = (fetchFn: FetchFn) => new KomgaClient('https://h/komga', { kind: 'apiKey', apiKey: 'k' }, fetchFn);

  it('시리즈 목록: POST 본문에 조건, 쿼리에 페이지', async () => {
    const page = { content: [], totalElements: 0, totalPages: 0, number: 0, last: true };
    const { calls, fetchFn } = fakeFetch(() => json(page));
    await client(fetchFn).getSeriesList(
      { condition: allOf(libraryIs('L1'), readStatusIs('IN_PROGRESS')) },
      { page: 0, size: 50, sort: 'metadata.titleSort,asc' },
    );
    expect(calls[0].url).toBe('https://h/komga/api/v1/series/list?page=0&size=50&sort=metadata.titleSort%2Casc');
    expect(calls[0].init?.method).toBe('POST');
    expect(JSON.parse(calls[0].init?.body as string)).toEqual({
      condition: {
        allOf: [{ libraryId: { operator: 'is', value: 'L1' } }, { readStatus: { operator: 'is', value: 'IN_PROGRESS' } }],
      },
    });
  });

  it('읽기 진행: PATCH {page, completed}, 204', async () => {
    const { calls, fetchFn } = fakeFetch(() => new Response(null, { status: 204 }));
    await client(fetchFn).markReadProgress('b1', 12, false);
    expect(calls[0].url).toBe('https://h/komga/api/v1/books/b1/read-progress');
    expect(calls[0].init?.method).toBe('PATCH');
    expect(JSON.parse(calls[0].init?.body as string)).toEqual({ page: 12, completed: false });
  });

  it('이미지: 인증 헤더로 받아 Blob', async () => {
    const { calls, fetchFn } = fakeFetch(() => new Response(new Uint8Array([1, 2, 3]), { status: 200 }));
    const blob = await client(fetchFn).fetchImage('api/v1/books/b1/pages/1');
    expect(blob.size).toBe(3);
    expect((calls[0].init?.headers as Record<string, string>)['X-API-Key']).toBe('k');
  });

  it('404는 HttpError로 던진다', async () => {
    const { fetchFn } = fakeFetch(() => new Response(null, { status: 404 }));
    await expect(client(fetchFn).fetchImage('api/v1/series/s1/thumbnail')).rejects.toMatchObject({
      name: 'HttpError',
      status: 404,
    });
  });
});
