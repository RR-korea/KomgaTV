import { describe, expect, it } from 'vitest';
import { apiUrl, ImagePaths, normalizeServerUrl } from '../src/api/url';

describe('normalizeServerUrl', () => {
  it('끝 슬래시를 떼고 하위 경로는 남긴다', () => {
    expect(normalizeServerUrl('https://host.example/komga/')).toBe('https://host.example/komga');
    expect(normalizeServerUrl('  http://192.168.0.10:25600  ')).toBe('http://192.168.0.10:25600');
  });

  it('쿼리·해시는 버린다', () => {
    expect(normalizeServerUrl('https://host.example/komga?x=1#y')).toBe('https://host.example/komga');
  });

  it('빈 값·스킴 없음·http(s) 아님은 null', () => {
    expect(normalizeServerUrl('')).toBeNull();
    expect(normalizeServerUrl('host.example')).toBeNull();
    expect(normalizeServerUrl('ftp://host.example')).toBeNull();
  });
});

describe('apiUrl', () => {
  it('하위 경로 아래에 api 경로와 쿼리를 붙인다', () => {
    expect(apiUrl('https://h/komga', 'api/v1/libraries')).toBe('https://h/komga/api/v1/libraries');
    expect(apiUrl('https://h', 'api/v1/series/list', { page: 0, size: 50, sort: 'metadata.titleSort,asc' })).toBe(
      'https://h/api/v1/series/list?page=0&size=50&sort=metadata.titleSort%2Casc',
    );
  });

  it('undefined 쿼리 값은 뺀다', () => {
    expect(apiUrl('https://h', 'a', { page: undefined, size: 1 })).toBe('https://h/a?size=1');
  });
});

describe('ImagePaths', () => {
  it('페이지 번호는 1부터', () => {
    expect(ImagePaths.bookPage('b1', 1)).toBe('api/v1/books/b1/pages/1');
    expect(ImagePaths.seriesThumbnail('s1')).toBe('api/v1/series/s1/thumbnail');
  });
});
