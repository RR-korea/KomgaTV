// 서버 주소 정리와 API URL 조립. 리버스 프록시 하위 경로(https://host/komga)를 지원한다.

/** 사용자가 입력한 서버 주소를 끝 슬래시 없는 http(s) URL로. 형식이 틀리면 null. */
export function normalizeServerUrl(input: string): string | null {
  const trimmed = input.trim();
  if (!trimmed) return null;
  let url: URL;
  try {
    url = new URL(trimmed);
  } catch {
    return null;
  }
  if ((url.protocol !== 'http:' && url.protocol !== 'https:') || !url.hostname) return null;
  url.search = '';
  url.hash = '';
  return url.toString().replace(/\/+$/, '');
}

/** baseUrl 아래의 API 경로 (path는 "api/..." 처럼 슬래시 없이 시작). */
export function apiUrl(baseUrl: string, path: string, query?: Record<string, string | number | undefined>): string {
  let url = `${baseUrl}/${path}`;
  if (query) {
    const params = Object.keys(query)
      .filter((key) => query[key] !== undefined)
      .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(String(query[key]))}`);
    if (params.length) url += `?${params.join('&')}`;
  }
  return url;
}

// 이미지 경로. baseUrl 기준 상대 경로로 두고 이미지 로더가 인증 fetch로 받는다 (PLAN Z3).
export const ImagePaths = {
  seriesThumbnail: (seriesId: string) => `api/v1/series/${seriesId}/thumbnail`,
  bookThumbnail: (bookId: string) => `api/v1/books/${bookId}/thumbnail`,
  /** page는 1부터 */
  bookPage: (bookId: string, page: number) => `api/v1/books/${bookId}/pages/${page}`,
};
