// EPUB 장(XHTML) → 화면에 넣을 HTML (브라우저 DOM 사용).
// - 스크립트·폼·외부 CSS·이벤트 속성을 지운다 (책 CSS 대신 리더 스타일을 쓴다 — Android T12와 같은 어두운 화면)
// - 링크는 눌러도 앱을 벗어나지 않게 href를 뗀다
// - 그림은 인증 헤더가 필요해 src를 떼고 data-epub-img 번호를 단다. 화면이 받아서 blob URL로 채운다.

const REMOVE = ['script', 'style', 'link', 'iframe', 'object', 'embed', 'form', 'input', 'button', 'textarea', 'select', 'meta', 'base', 'video', 'audio'];
const XLINK = 'http://www.w3.org/1999/xlink';

export interface PreparedChapter {
  html: string;
  /** data-epub-img 번호 순서의 그림 전체 URL */
  images: string[];
}

export function prepareChapter(source: string, chapterUrl: string): PreparedChapter {
  const parser = new DOMParser();
  let doc = parser.parseFromString(source, 'application/xhtml+xml');
  if (doc.getElementsByTagName('parsererror').length > 0) doc = parser.parseFromString(source, 'text/html');
  const body = (doc.body ?? doc.getElementsByTagName('body')[0] ?? doc.documentElement) as Element;

  for (const tag of REMOVE) {
    Array.from(body.getElementsByTagName(tag)).forEach((node) => node.parentNode?.removeChild(node));
  }

  const images: string[] = [];
  const resolve = (value: string) => {
    try {
      return new URL(value, chapterUrl).href;
    } catch {
      return null;
    }
  };

  Array.from(body.getElementsByTagName('*')).forEach((element) => {
    Array.from(element.attributes).forEach((attr) => {
      if (attr.name.toLowerCase().startsWith('on')) element.removeAttribute(attr.name);
    });
    const tag = element.localName.toLowerCase();
    if (tag === 'a') {
      element.removeAttribute('href');
      element.removeAttributeNS(XLINK, 'href');
    } else if (tag === 'img') {
      const url = resolve(element.getAttribute('src') ?? '');
      element.removeAttribute('src');
      element.removeAttribute('srcset');
      if (url) element.setAttribute('data-epub-img', String(images.push(url) - 1));
    } else if (tag === 'image') {
      // SVG <image href|xlink:href>
      const raw = element.getAttribute('href') ?? element.getAttributeNS(XLINK, 'href') ?? '';
      const url = resolve(raw);
      element.removeAttribute('href');
      element.removeAttributeNS(XLINK, 'href');
      if (url) element.setAttribute('data-epub-img', String(images.push(url) - 1));
    }
  });

  return { html: body.innerHTML, images };
}
