// 개발용 가짜 Komga 서버. dist/를 / 에서, API를 /komga/api 아래(하위 경로)에서 낸다.
// 실행: npm run build && node dev/mock-komga.mjs [port]   → http://localhost:8790/
// 로그인: test@example.com / pw  또는 API 키 test
// 실제 서버 데이터 형태(긴 폴더 이름, 번호뿐인 책 이름, 가로 펼침 쪽, 표지 없는 시리즈)를 흉내 낸다.
import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';

const port = Number(process.argv[2] ?? 8790);
const root = 'dist';
const BASE = '/komga';

const libraries = [
  { id: 'L1', name: '만화' },
  { id: 'L2', name: 'Comics' },
];

const series = [];
for (let i = 1; i <= 130; i++) {
  const libraryId = i <= 120 ? 'L1' : 'L2';
  const name = i === 1 ? '[만화,연재중] 킹덤(1-72) - 야수히사 하라' : i === 2 ? '원피스' : `시리즈 ${String(i).padStart(3, '0')}`;
  series.push({
    id: `S${i}`,
    libraryId,
    name,
    booksCount: 5,
    booksReadCount: 0,
    booksUnreadCount: 5,
    booksInProgressCount: 0,
    metadata: {
      title: name,
      summary: i === 1 ? '중국 춘추전국시대를 배경으로 한 대하 역사 만화. '.repeat(6) : '',
      status: i % 3 === 0 ? 'ENDED' : 'ONGOING',
      publisher: i % 2 ? '대원씨아이' : '',
      readingDirection: i === 2 ? 'RIGHT_TO_LEFT' : null,
    },
  });
}

const PAGES = 24;
const books = [];
for (const s of series) {
  for (let n = 1; n <= 5; n++) {
    books.push({
      id: `${s.id}B${n}`,
      seriesId: s.id,
      seriesTitle: s.metadata.title,
      name: String(n).padStart(2, '0'),
      number: n,
      media: { pagesCount: PAGES },
      metadata: { title: String(n).padStart(2, '0'), number: String(n) },
      readProgress: null,
      readDate: 0,
    });
  }
}
// 읽는 중인 책 몇 권
for (const [id, page] of [['S1B3', 7], ['S2B1', 12], ['S5B2', 3]]) {
  const book = books.find((b) => b.id === id);
  book.readProgress = { page, completed: false };
  book.readDate = Date.now() - books.indexOf(book);
}

// EPUB 책 하나 (Android T12): S7B1, 3장, 둘째 장은 길고 그림이 있다. 둘째 장 처음에서 읽던 중
const EPUB_ID = 'S7B1';
{
  const book = books.find((b) => b.id === EPUB_ID);
  book.media = { pagesCount: 30, mediaProfile: 'EPUB' };
  book.metadata.title = 'a67879a1d4464860c3eedc980bd12118'; // 파일 해시 제목 → 시리즈 이름으로 보여야 한다
  book.readProgress = { page: 10, completed: false };
  book.readDate = Date.now() - 10_000_000; // 읽는 중 줄의 맨 뒤
}
const epubChapters = ['index_split_000.html', 'index_split_001.html', 'index_split_002.html'];
const epubHtml = (i) =>
  `<?xml version='1.0' encoding='utf-8'?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>t</title>` +
  `<link href="stylesheet.css" rel="stylesheet" type="text/css"/><script>window.bad = 1</script></head>` +
  `<body class="calibre"><h1>${['첫째', '둘째', '셋째'][i]} 장</h1>` +
  (i === 1 ? '<p><img src="images/pic.svg" alt=""/></p>' : '') +
  Array.from({ length: i === 1 ? 120 : 8 }, (_, n) => `<p onclick="alert(1)">${['첫째', '둘째', '셋째'][i]} 장 문단 ${n + 1}. 리모컨으로 읽는 글입니다.</p>`).join('') +
  `<p><a href="index_split_002.html">다음 장으로</a></p></body></html>`;
let epubProgression = {
  modified: new Date().toISOString(),
  device: { id: 'x', name: 'x' },
  locator: { href: 'index_split_001.html', type: 'application/xhtml+xml', locations: { progression: 0, totalProgression: 0.33 } },
};

/** 8쪽·9쪽은 가로(펼침 스캔), 나머지 세로 */
const pageSize = (n) => (n === 8 || n === 15 ? { width: 1600, height: 1200 } : { width: 800, height: 1200 });

const json = (res, body, status = 200) => {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
};

const svg = (res, width, height, label, hue) => {
  res.writeHead(200, { 'Content-Type': 'image/svg+xml' });
  res.end(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">` +
      `<rect width="100%" height="100%" fill="hsl(${hue},45%,35%)"/>` +
      `<rect x="20" y="20" width="${width - 40}" height="${height - 40}" fill="none" stroke="#fff" stroke-width="6"/>` +
      `<text x="50%" y="50%" fill="#fff" font-size="${Math.round(height / 10)}" font-family="sans-serif" text-anchor="middle" dominant-baseline="middle">${label}</text>` +
      `</svg>`,
  );
};

function authorized(req) {
  if (req.headers['x-api-key'] === 'test') return true;
  const header = req.headers.authorization ?? '';
  return header === `Basic ${Buffer.from('test@example.com:pw').toString('base64')}`;
}

function paged(res, items, url) {
  const page = Number(url.searchParams.get('page') ?? 0);
  const size = Number(url.searchParams.get('size') ?? 20);
  const content = items.slice(page * size, page * size + size);
  json(res, {
    content,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
    number: page,
    last: (page + 1) * size >= items.length,
  });
}

function conditionValue(condition, field) {
  if (!condition) return undefined;
  if (condition[field]) return condition[field].value;
  for (const child of condition.allOf ?? []) {
    const value = conditionValue(child, field);
    if (value !== undefined) return value;
  }
  return undefined;
}

const readBody = (req) =>
  new Promise((resolve) => {
    let body = '';
    req.on('data', (chunk) => (body += chunk));
    req.on('end', () => resolve(body ? JSON.parse(body) : {}));
  });

async function api(req, res, path, url) {
  if (!authorized(req)) return json(res, { error: 'Unauthorized' }, 401);
  let m;
  if (path === 'api/v2/users/me') return json(res, { id: 'U1', email: 'test@example.com' });
  if (path === 'api/v1/libraries') return json(res, libraries);
  // 홈 줄
  if (path === 'api/v1/books/ondeck') return paged(res, books.filter((b) => b.number === 2 && !b.readProgress).slice(0, 7), url);
  if (path === 'api/v1/books/latest') return paged(res, books.slice().reverse(), url);
  if (path === 'api/v1/series/new') return paged(res, series.slice().reverse(), url);
  if (path === 'api/v1/series/updated') return paged(res, series.slice(10, 30), url);
  if (path === 'api/v1/series/list' && req.method === 'POST') {
    const { condition, fullTextSearch } = await readBody(req);
    const libraryId = conditionValue(condition, 'libraryId');
    const list = series.filter(
      (s) => (!libraryId || s.libraryId === libraryId) && (!fullTextSearch || s.metadata.title.includes(fullTextSearch)),
    );
    list.sort((a, b) => a.metadata.title.localeCompare(b.metadata.title));
    return paged(res, list, url);
  }
  if ((m = path.match(/^api\/v1\/series\/([^/]+)$/))) {
    const found = series.find((s) => s.id === m[1]);
    return found ? json(res, found) : json(res, {}, 404);
  }
  if ((m = path.match(/^api\/v1\/series\/([^/]+)\/thumbnail$/))) {
    if (m[1] === 'S4') return json(res, {}, 404); // 표지 없는 시리즈
    return svg(res, 300, 450, m[1], (Number(m[1].slice(1)) * 37) % 360);
  }
  if (path === 'api/v1/books/list' && req.method === 'POST') {
    const { condition, fullTextSearch } = await readBody(req);
    const seriesId = conditionValue(condition, 'seriesId');
    const status = conditionValue(condition, 'readStatus');
    let list = books.filter(
      (b) => (!seriesId || b.seriesId === seriesId) && (!fullTextSearch || `${b.seriesTitle} ${b.name}`.includes(fullTextSearch)),
    );
    if (status === 'IN_PROGRESS') {
      list = list.filter((b) => b.readProgress && !b.readProgress.completed);
      list.sort((a, b) => b.readDate - a.readDate);
    } else {
      list.sort((a, b) => a.number - b.number);
    }
    return paged(res, list, url);
  }
  // 다음 권 (같은 시리즈의 다음 번호, 없으면 404)
  if ((m = path.match(/^api\/v1\/books\/([^/]+)\/next$/))) {
    const book = books.find((b) => b.id === m[1]);
    const next = book && books.find((b) => b.seriesId === book.seriesId && b.number === book.number + 1);
    return next ? json(res, next) : json(res, {}, 404);
  }
  // 책·라이브러리 메뉴 (Android T13)
  if ((m = path.match(/^api\/v1\/books\/([^/]+)\/read-progress$/)) && req.method === 'DELETE') {
    const book = books.find((b) => b.id === m[1]);
    if (book) book.readProgress = null;
    console.log(`DELETE read-progress ${m[1]}`);
    res.writeHead(204);
    return res.end();
  }
  if ((m = path.match(/^api\/v1\/books\/([^/]+)\/analyze$/)) && req.method === 'POST') {
    console.log(`POST analyze ${m[1]}`);
    res.writeHead(202);
    return res.end();
  }
  if ((m = path.match(/^api\/v1\/libraries\/([^/]+)\/(scan|empty-trash)$/)) && req.method === 'POST') {
    console.log(`POST ${m[2]} ${m[1]}`);
    res.writeHead(202);
    return res.end();
  }
  if ((m = path.match(/^api\/v1\/books\/([^/]+)\/manifest$/)) && m[1] === EPUB_ID) {
    const base = `http://localhost:${port}${BASE}/api/v1/books/${EPUB_ID}/resource/`;
    res.writeHead(200, { 'Content-Type': 'application/webpub+json' });
    return res.end(
      JSON.stringify({
        readingOrder: epubChapters.map((f) => ({ href: base + f, type: 'application/xhtml+xml' })),
        toc: [{ title: '둘째 장 제목', href: `${base}index_split_001.html` }],
      }),
    );
  }
  if ((m = path.match(/^api\/v1\/books\/([^/]+)\/resource\/(.+)$/)) && m[1] === EPUB_ID) {
    const file = m[2];
    if (file === 'images/pic.svg') return svg(res, 600, 300, 'EPUB 그림', 200);
    const i = epubChapters.indexOf(file);
    if (i < 0) return json(res, {}, 404);
    res.writeHead(200, { 'Content-Type': 'application/xhtml+xml' });
    return res.end(epubHtml(i));
  }
  if ((m = path.match(/^api\/v1\/books\/([^/]+)\/progression$/)) && m[1] === EPUB_ID) {
    if (req.method === 'PUT') {
      epubProgression = await readBody(req);
      const loc = epubProgression.locator;
      console.log(`PUT progression ${EPUB_ID} href=${loc.href} p=${loc.locations.progression.toFixed(2)} total=${loc.locations.totalProgression.toFixed(2)}`);
      res.writeHead(204);
      return res.end();
    }
    return json(res, epubProgression);
  }
  if ((m = path.match(/^api\/v1\/books\/([^/]+)$/))) {
    const found = books.find((b) => b.id === m[1]);
    return found ? json(res, found) : json(res, {}, 404);
  }
  if ((m = path.match(/^api\/v1\/books\/([^/]+)\/thumbnail$/))) {
    return svg(res, 300, 450, m[1], (m[1].length * 53) % 360);
  }
  if ((m = path.match(/^api\/v1\/books\/([^/]+)\/pages$/))) {
    return json(
      res,
      Array.from({ length: PAGES }, (_, i) => ({ number: i + 1, mediaType: 'image/svg+xml', ...pageSize(i + 1) })),
    );
  }
  if ((m = path.match(/^api\/v1\/books\/([^/]+)\/pages\/(\d+)$/))) {
    const n = Number(m[2]);
    const { width, height } = pageSize(n);
    return svg(res, width, height, `${m[1]} p${n}`, (n * 29) % 360);
  }
  if ((m = path.match(/^api\/v1\/books\/([^/]+)\/read-progress$/)) && req.method === 'PATCH') {
    const book = books.find((b) => b.id === m[1]);
    const { page, completed } = await readBody(req);
    book.readProgress = { page, completed };
    book.readDate = Date.now();
    console.log(`PATCH read-progress ${book.id} page=${page} completed=${completed}`);
    res.writeHead(204);
    return res.end();
  }
  return json(res, {}, 404);
}

const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.xml': 'text/xml' };

createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  // 패키지 앱(file://)과 같은 교차 출처 상황도 흉내 낼 수 있게 CORS를 연다
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'Authorization, X-API-Key, Content-Type');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PATCH, OPTIONS');
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    return res.end();
  }
  if (url.pathname.startsWith(`${BASE}/`)) {
    return api(req, res, url.pathname.slice(BASE.length + 1), url).catch((error) => {
      console.error(error);
      json(res, {}, 500);
    });
  }
  const file = normalize(join(root, url.pathname === '/' ? 'index.html' : url.pathname));
  if (!file.startsWith(normalize(root)) || !existsSync(file) || !statSync(file).isFile()) {
    res.writeHead(404);
    return res.end();
  }
  res.writeHead(200, { 'Content-Type': types[extname(file)] ?? 'application/octet-stream' });
  createReadStream(file).pipe(res);
}).listen(port, () => console.log(`mock Komga: http://localhost:${port}${BASE}  app: http://localhost:${port}/`));
