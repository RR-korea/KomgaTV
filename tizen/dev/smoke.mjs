// 리모컨 키만으로 전체 흐름을 도는 스모크 테스트 (PC Edge, 1920×1080, 가짜 Komga 서버).
// 실행: npm run build && node dev/smoke.mjs [스크린샷 폴더]
// 가짜 서버(dev/mock-komga.mjs)는 이 스크립트가 띄운다. 실기 TV 확인을 대신하지 않는다.
import { spawn } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { chromium } from 'playwright-core';

const shots = process.argv[2] ?? 'dev/shots';
mkdirSync(shots, { recursive: true });
const port = 8791;
const server = spawn(process.execPath, ['dev/mock-komga.mjs', String(port)], { stdio: ['ignore', 'pipe', 'inherit'] });
const patches = [];
server.stdout.on('data', (chunk) => {
  const text = String(chunk);
  for (const line of text.split('\n')) {
    if (/^(PATCH|PUT progression|DELETE|POST|RAW|RENDER)/.test(line)) patches.push(line.trim());
  }
});
await new Promise((resolve) => server.stdout.once('data', resolve));

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, locale: process.env.LOCALE ?? 'ko-KR' });
const errors = [];
page.on('pageerror', (error) => errors.push(String(error)));
// 4xx 리소스 로그는 일부러 만든 것(401 시험, 표지 없는 시리즈 404)이라 따로 센다
page.on('console', (message) => {
  if (message.type() !== 'error') return;
  if (/status of 40[14]/.test(message.text())) return;
  errors.push(message.text());
});

let step = 0;
const results = [];
const focused = () => page.evaluate(() => document.activeElement?.dataset?.key ?? null);
const key = async (name, times = 1) => {
  for (let i = 0; i < times; i++) {
    await page.keyboard.press(name);
    await page.waitForTimeout(120);
  }
};
const longPress = async () => {
  await page.keyboard.down('Enter');
  await page.waitForTimeout(900);
  await page.keyboard.up('Enter');
  await page.waitForTimeout(300);
};
const shot = async (name) => {
  await page.waitForTimeout(400);
  await page.screenshot({ path: join(shots, `${String(++step).padStart(2, '0')}-${name}.png`) });
};
const expectFocus = async (label, expected) => {
  const actual = await focused();
  const ok = typeof expected === 'function' ? expected(actual) : actual === expected;
  results.push(`${ok ? 'PASS' : 'FAIL'} ${label}: focus=${actual}${ok ? '' : ` (expected ${expected})`}`);
};
const expectText = async (label, selector, pattern) => {
  const text = await page.evaluate((s) => document.querySelector(s)?.textContent ?? '', selector);
  const ok = pattern.test(text);
  results.push(`${ok ? 'PASS' : 'FAIL'} ${label}: "${text.slice(0, 80)}"`);
};
const typeInto = async (text) => {
  await key('Enter');
  await page.keyboard.type(text);
  await key('Enter');
};

try {
  await page.goto(`http://localhost:${port}/`);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  await page.waitForTimeout(300);

  // 1. 첫 실행 → 설정, URL 칸 포커스
  await expectFocus('첫 실행 URL 칸', 'url');
  // 서버가 없어도 둘러볼 수 있게 (Android 1.0.5) — 실제 데모 서버(demo.komga.org)에는 연결하지 않는다
  await expectText('첫 실행: 데모 서버 안내', '.settings', /Komga 공식 데모 서버/);
  results.push(`${(await page.evaluate(() => !!document.querySelector('[data-key="server-demo"]'))) ? 'PASS' : 'FAIL'} 첫 실행: 데모 서버 버튼`);
  await typeInto(`http://localhost:${port}/komga/`);
  await key('ArrowDown');
  await expectFocus('인증 방식 행은 선택된 항목으로', 'auth-basic');
  await key('ArrowDown');
  await expectFocus('이메일 칸', 'email');
  await typeInto('test@example.com');
  await key('ArrowDown');
  await typeInto('wrong');
  await key('ArrowDown');
  await expectFocus('연결 테스트 버튼', 'test');
  await key('Enter');
  await page.waitForTimeout(500);
  await expectText('잘못된 비밀번호 → 401 문구', '.status', /401/);
  await shot('settings-401');
  await key('ArrowUp');
  await expectFocus('위로 → 비밀번호', 'password');
  await key('Enter');
  await page.keyboard.press('Control+A');
  await page.keyboard.type('pw');
  await key('Enter');
  await key('ArrowDown');
  await key('ArrowRight');
  await expectFocus('저장 버튼', 'save');
  await key('Enter');
  await page.waitForTimeout(800);

  // 2. 홈
  await expectFocus('홈 진입 → 이어 읽기 첫 책', 'reading-book-S1B3');
  await shot('home');
  await key('ArrowRight');
  await expectFocus('오른쪽 → 둘째 책', 'reading-book-S2B1');
  await key('ArrowLeft');
  await key('Enter');
  await page.waitForTimeout(800);

  // 3. 리더: 진행 7쪽에서 시작
  await shot('reader-p7');
  await key('ArrowRight');
  await page.waitForTimeout(300);
  await shot('reader-p8-landscape');
  await key('Enter');
  await expectFocus('OK → 정보, 보기 설정 버튼', 'reader-settings');
  await expectText('정보: 8/24쪽', '.reader-overlay-row:nth-child(2)', /8 \/ 24/);
  await shot('reader-overlay');
  await key('Enter');
  await expectFocus('한 번 더 OK → 패널 첫 항목(리더 종류)', 'opt-type');
  await key('ArrowDown');
  await expectFocus('아래 → 쪽 배치', 'opt-layout');
  await key('ArrowRight');
  await shot('reader-panel-double');
  await expectText('패널 상태 줄: 이 작품만의 설정', '.reader-panel-status', /이 작품만의 설정/);
  await key('ArrowDown', 3);
  await expectFocus('패널 아래로 3 → 크기 맞춤', 'opt-scale');
  await key('Escape');
  await key('Escape');
  await key('ArrowRight');
  await page.waitForTimeout(300);
  const shown = await page.evaluate(() => document.querySelectorAll('.reader-page').length);
  results.push(`${shown === 2 ? 'PASS' : 'FAIL'} 두 쪽 보기 → 두 장 표시: ${shown}장`);
  // 화면에 맞춤: 세로 쪽 두 장이 화면 높이(1080)를 채워야 한다 (스타일 크기가 실제로 적용됐는지)
  const heights = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.reader-page')).map((el) => Math.round(el.getBoundingClientRect().height)),
  );
  results.push(`${heights.every((h) => h === 1080) ? 'PASS' : 'FAIL'} 화면에 맞춤 높이 1080: ${heights.join(',')}`);
  await shot('reader-double-9-10');
  await key('Enter');
  await expectText('두 쪽 보기 → 9,10쪽 펼침', '.reader-overlay-row:nth-child(2)', /9, 10 \/ 24/);
  // 작품별 확대 (Android 0.2.5, 0.2.8부터 100~120%) + 화면 전환 (0.2.9): 크기 맞춤 → 화면 전환 → 확대
  await key('Enter');
  await key('ArrowDown', 5);
  await expectFocus('패널: 크기 맞춤 아래 화면 전환', 'opt-transition');
  await expectText('화면 전환 기본값 밀기', '[data-key="opt-transition"] .view-row-value', /밀기/);
  results.push(`${(await page.evaluate(() => !document.body.textContent.includes('작은 이미지 키우기'))) ? 'PASS' : 'FAIL'} 작은 이미지 키우기 줄 없음`);
  await key('ArrowDown');
  await expectFocus('패널: 화면 전환 아래 확대', 'opt-zoom');
  await key('ArrowLeft');
  await expectText('확대: 100%에서 ◀는 그대로', '[data-key="opt-zoom"] .view-row-value', /^‹100%›$/);
  await key('ArrowRight', 25);
  await expectText('확대: ▶ 25번 → 120%에서 멈춤', '[data-key="opt-zoom"] .view-row-value', /^‹120%›$/);
  await key('Escape');
  await key('Escape');
  await page.waitForTimeout(300);
  const zoomed = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.reader-page')).map((el) => Math.round(el.getBoundingClientRect().height)),
  );
  results.push(`${zoomed.length === 2 && zoomed.every((h) => h === 1296) ? 'PASS' : 'FAIL'} 확대 120%: 두 쪽이 함께 1296: ${zoomed.join(',')}`);
  const spreadTop = () =>
    page.evaluate(() => Math.round(document.querySelector('.reader-spread').getBoundingClientRect().top));
  results.push(`${(await spreadTop()) === -108 ? 'PASS' : 'FAIL'} 확대: 가운데 기준 (위 -108)`);
  await key('ArrowDown', 3);
  results.push(`${(await spreadTop()) === -216 ? 'PASS' : 'FAIL'} 확대: ▼로 아래 끝까지 (위 -216, 더 안 내려감)`);
  await shot('reader-zoom-120-bottom');
  const layers = () =>
    page.evaluate(() => Array.from(document.querySelectorAll('.reader-layer')).map((el) => el.className.replace('reader-layer', '').trim() || '-'));
  await key('ArrowRight');
  const sliding = await layers();
  results.push(`${sliding.includes('slide-out-left') && sliding.includes('slide-in-right') ? 'PASS' : 'FAIL'} 밀기: 다음 펼침이 오른쪽에서 (${sliding.join(',')})`);
  await page.waitForTimeout(400);
  const settled = await layers();
  results.push(`${settled.length === 1 && settled[0] === '-' ? 'PASS' : 'FAIL'} 밀기 끝나면 한 장만: ${settled.join(',')}`);
  results.push(`${(await spreadTop()) === -108 ? 'PASS' : 'FAIL'} 확대: 펼침이 바뀌면 다시 가운데`);
  await key('ArrowLeft');
  const backward = await layers();
  // 바로 앞 넘김에서 0.4초 지났으니 연타 아님 → 이전 펼침은 왼쪽에서
  results.push(`${backward.includes('slide-in-left') && backward.includes('slide-out-right') ? 'PASS' : 'FAIL'} 밀기: 이전 펼침은 왼쪽에서 (${backward.join(',')})`);
  await key('ArrowRight');
  const rapid = await layers();
  results.push(`${rapid.length === 1 ? 'PASS' : 'FAIL'} 0.3초 안에 연달아 넘기면 효과 없이 바로: ${rapid.join(',')}`);
  await page.waitForTimeout(400);
  await key('ArrowLeft');
  await page.waitForTimeout(400);
  // 페이드로 바꿔 본다
  await key('Enter');
  await key('Enter');
  await key('ArrowDown', 5);
  await key('ArrowRight');
  await expectText('화면 전환 → 페이드', '[data-key="opt-transition"] .view-row-value', /페이드/);
  await key('ArrowDown');
  await key('ArrowLeft', 25);
  await expectText('확대 되돌림 100%', '[data-key="opt-zoom"] .view-row-value', /100%/);
  await key('Escape');
  await key('Escape');
  await page.waitForTimeout(400);
  await key('ArrowRight');
  const fading = await layers();
  results.push(`${fading.includes('fade-in') && fading.includes('fade-out') ? 'PASS' : 'FAIL'} 페이드: 겹쳐 바꾸기 (${fading.join(',')})`);
  await page.waitForTimeout(400);
  await key('ArrowLeft');
  await page.waitForTimeout(400);
  await key('Enter');
  await key('Enter');
  await key('ArrowDown', 5);
  await key('ArrowLeft'); // 페이드 → 밀기로 되돌림
  await expectText('화면 전환 되돌림 밀기', '[data-key="opt-transition"] .view-row-value', /밀기/);
  await key('Escape');
  await key('Escape');
  await key('Escape'); // 나가기 → 즉시 저장
  await page.waitForTimeout(1200);

  // 4. 홈 복귀: 새로고침 + 연 책 포커스
  await expectFocus('리더에서 돌아오면 연 책', 'reading-book-S1B3');
  results.push(`${patches.some((p) => p.includes('S1B3 page=10')) ? 'PASS' : 'FAIL'} 나갈 때 진행 저장 (보이는 쪽 중 큰 번호 10): ${patches.join(' | ')}`);
  await expectText('홈 진행도 갱신', '[data-key="reading-book-S1B3"] .card-subtitle', /10\/24/);
  await shot('home-after-reader');

  // 5. 오른쪽→왼쪽 시리즈(원피스): 왼쪽 키가 다음
  await key('ArrowRight');
  await key('Enter');
  await page.waitForTimeout(800);
  await key('ArrowLeft');
  await page.waitForTimeout(300);
  await key('Enter');
  await expectText('RTL: 왼쪽 → 다음 쪽 (12→13)', '.reader-overlay-row:nth-child(2)', /13/);
  // 작품별 보기 설정 (Android T14): 앞 책(S1)에서 바꾼 두 쪽 보기는 이 작품(S2)에 번지지 않는다
  await expectText('다른 작품은 앱 기본값(한 쪽)', '.reader-overlay-row:nth-child(2)', /^13 \/ 24쪽/);
  await key('Escape');
  await key('Escape');
  await page.waitForTimeout(800);
  await expectFocus('RTL 책에서 돌아오면 그 책', 'reading-book-S2B1');

  // 5-2. 세로 스크롤(웹툰): 진행 3쪽인 책을 열어 리더 종류를 바꾼다.
  // 이어 읽기는 최근 읽은 순이라 지금 순서는 S2B1, S1B3, S5B2
  await key('ArrowRight', 2);
  await expectFocus('셋째 책', 'reading-book-S5B2');
  await key('Enter');
  await page.waitForTimeout(800);
  await key('Enter');
  await key('Enter');
  await key('ArrowRight'); // 리더 종류 → 세로 스크롤
  await expectText('패널: 세로 스크롤', '[data-key="opt-type"] .view-row-value', /세로 스크롤/);
  await key('ArrowDown');
  await expectFocus('세로 스크롤 패널: 좌우 여백', 'opt-padding');
  await key('ArrowRight', 2); // 10%
  await shot('continuous-panel');
  await key('Escape');
  await key('Escape');
  const column = () =>
    page.evaluate(() => {
      const el = document.querySelector('.reader-column');
      const first = el?.querySelector('.reader-page');
      return {
        transform: el?.getAttribute('style') ?? '',
        width: first ? Math.round(first.getBoundingClientRect().width) : 0,
      };
    });
  const start = await column();
  results.push(`${start.width === 1536 ? 'PASS' : 'FAIL'} 좌우 여백 10% → 쪽 너비 1536: ${start.width}`);
  await key('ArrowDown');
  await page.waitForTimeout(400);
  const after = await column();
  results.push(`${after.transform !== start.transform ? 'PASS' : 'FAIL'} 아래 → 한 화면 스크롤: ${after.transform.match(/translateY\([^)]+\)/)?.[0]}`);
  // 이 작품은 앱 기본값(한 쪽)이라 3쪽에서 시작 → 오른쪽 두 번이면 5쪽 맨 위
  await key('ArrowRight', 2);
  await page.waitForTimeout(400);
  await key('Enter');
  await expectText('오른쪽 ×2 → 5쪽', '.reader-overlay-row:nth-child(2)', /5 \/ 24/);
  await shot('continuous-overlay');
  await key('Escape');
  await key('Escape');
  await page.waitForTimeout(1000);
  results.push(`${patches.some((p) => p.includes('S5B2 page=5')) ? 'PASS' : 'FAIL'} 세로 스크롤 진행 저장 5쪽: ${patches.filter((p) => p.includes('S5B2')).join(' | ')}`);
  await expectFocus('세로 스크롤 책에서 돌아오면 그 책', 'reading-book-S5B2');
  await page.waitForTimeout(1500); // 돌아온 뒤 조용한 새로고침이 끝나기를 기다린다

  // 5-3. 세로 스크롤의 책 경계 (Android ContinuousPages와 같게): 첫 카드 → OK = 이전 권 끝(책 끝이 화면 아래), 끝 카드 → OK = 다음 권
  await key('Enter'); // S5B2 다시 (5쪽, 세로 스크롤)
  await page.waitForTimeout(1000);
  await key('ArrowLeft', 5); // 5→4→3→2→1→책 첫 카드
  await page.waitForTimeout(500);
  const cardAt = (which) =>
    page.evaluate((w) => {
      const cards = document.querySelectorAll('.end-card');
      const card = w === 'last' ? cards[cards.length - 1] : cards[0];
      if (!card) return null;
      const r = card.getBoundingClientRect();
      return { top: Math.round(r.top), bottom: Math.round(r.bottom), text: card.textContent ?? '' };
    }, which);
  const startCard = await cardAt('first');
  results.push(`${startCard && startCard.top === 0 && /이전 권/.test(startCard.text) ? 'PASS' : 'FAIL'} 1쪽에서 왼쪽 → 맨 위 책 첫 카드(한 화면): top=${startCard?.top}`);
  await key('ArrowRight');
  await page.waitForTimeout(400);
  await key('Enter');
  await expectText('첫 카드에서 오른쪽 → 1쪽 (2쪽 아님)', '.reader-overlay-row:nth-child(2)', /^1 \/ 24/);
  await key('Escape');
  await key('ArrowLeft');
  await page.waitForTimeout(400);
  await key('Enter'); // 첫 카드에서 OK = 이전 권 마지막 쪽
  await page.waitForTimeout(1500);
  await key('Enter');
  await expectText('첫 카드 OK → 이전 권(01) 끝', '.reader-overlay-row:nth-child(1)', /01/);
  await expectText('이전 권은 마지막 쪽', '.reader-overlay-row:nth-child(2)', /24 \/ 24/);
  const lastPageBottom = await page.evaluate(() => {
    const pages = document.querySelectorAll('.reader-column .reader-page');
    const last = pages[pages.length - 1];
    return last ? Math.round(last.getBoundingClientRect().bottom) : null;
  });
  results.push(`${lastPageBottom !== null && Math.abs(lastPageBottom - 1080) <= 1 ? 'PASS' : 'FAIL'} 이전 권: 책 끝이 화면 아래에 (마지막 쪽 아래 ${lastPageBottom})`);
  await key('Escape');
  await key('ArrowDown'); // 한 화면 아래 = 맨 아래 책 끝 카드
  await page.waitForTimeout(500);
  const endCard = await cardAt('last');
  results.push(`${endCard && endCard.top === 0 && /다음 권/.test(endCard.text) ? 'PASS' : 'FAIL'} 맨 아래 책 끝 카드(한 화면): top=${endCard?.top}`);
  await key('Enter'); // 끝 카드에서 OK = 다음 권 (안내 문구 "▼ / OK")
  await page.waitForTimeout(1500);
  await key('Enter');
  // 첫 카드까지 거슬러 갔을 때 1쪽이 저장됐으므로(보이는 쪽 중 큰 번호, Android와 같음) 다음 권은 그 저장된 쪽에서 연다
  const savedS5B2 = patches.filter((p) => /S5B2 page=\d+/.test(p)).pop()?.match(/page=(\d+)/)?.[1] ?? '?';
  await expectText(`끝 카드 OK → 다음 권(02), 저장된 ${savedS5B2}쪽에서`, '.reader-overlay-row:nth-child(2)', new RegExp(`^${savedS5B2} / 24`));
  await expectText('다음 권 제목', '.reader-overlay-row:nth-child(1)', /02/);
  await key('Escape');
  await key('Escape');
  await page.waitForTimeout(1200);
  results.push(`${!patches.some((p) => p.includes('S5B1')) ? 'PASS' : 'FAIL'} 이전 권을 열어 보기만 하면 그 책 진행은 그대로`);
  await expectFocus('경계 시험 뒤 돌아오면 연 책', 'reading-book-S5B2');
  await page.waitForTimeout(1500);

  // 5-3. 홈 줄 (Android T9): 읽는 중 아래에 다음 권·최근 추가 책·최근 추가/업데이트 시리즈
  const rows = await page.evaluate(() => Array.from(document.querySelectorAll('.home .row-title')).map((el) => el.textContent));
  results.push(`${rows.join('|') === '읽는 중|다음 권|최근 추가된 책|최근 추가된 시리즈|최근 업데이트된 시리즈|라이브러리' ? 'PASS' : 'FAIL'} 홈 줄 순서: ${rows.join(' | ')}`);
  await key('ArrowDown');
  await expectFocus('아래 → 다음 권 줄', (k) => k?.startsWith('ondeck-book-'));
  await shot('home-rows');

  // 5-4. 검색 (Android T8): 위로 올라가 검색 버튼 → 입력 → 400ms 뒤 결과
  const trail = [];
  for (let i = 0; i < 2; i++) {
    await key('ArrowUp');
    trail.push(
      await page.evaluate(() => {
        const a = document.activeElement;
        const r = a?.getBoundingClientRect();
        const s = document.querySelector('[data-key="search"]')?.getBoundingClientRect();
        return `${a?.dataset?.key}@${r ? [r.left, r.top, r.width, r.height].map(Math.round) : '-'} search@${s ? [s.left, s.top, s.width, s.height].map(Math.round) : '-'} homeScroll=${document.querySelector('.screen:not(.hidden) .home')?.scrollTop}`;
      }),
    );
  }
  await expectFocus('위 → 검색 버튼', 'search');
  if ((await focused()) !== 'search') results.push(`  diag: ${trail.join(' → ')}`);
  await key('Enter');
  await page.waitForTimeout(500);
  await expectFocus('검색 화면 → 입력칸', 'query');
  // TV처럼 천천히: 한 글자 치고 결과가 온 뒤에도 입력이 이어져야 한다 (포커스를 빼앗아 키보드가 닫히면 안 됨)
  await key('Enter');
  await page.keyboard.type('원');
  await page.waitForTimeout(900);
  const stillTyping = await page.evaluate(() => document.activeElement?.tagName === 'INPUT');
  results.push(`${stillTyping ? 'PASS' : 'FAIL'} 결과가 와도 입력칸 유지 (키보드 안 닫힘)`);
  await page.keyboard.type('피');
  await key('Enter');
  await page.waitForTimeout(900);
  await expectText('검색 결과: 시리즈 1', '.search .row-title', /시리즈 \(1\)/);
  await key('ArrowDown');
  await expectFocus('입력칸 아래 → 첫 결과', 'search-series-S2');
  await shot('search');
  await key('Enter');
  await page.waitForTimeout(800);
  await key('Escape');
  await page.waitForTimeout(400);
  await expectFocus('시리즈에서 돌아오면 검색 결과 카드', 'search-series-S2');
  await key('ArrowUp');
  await typeInto('없는제목');
  await page.waitForTimeout(900);
  await expectText('결과 없음 문구', '.search-message', /에 맞는 시리즈나 책이 없습니다/);
  // 줄마다 이어 불러오기 (Android T10): "시리즈"는 가짜 서버에서 128개
  await key('Enter');
  await page.keyboard.press('Control+A');
  await page.keyboard.type('시리즈');
  await key('Enter');
  await page.waitForTimeout(900);
  await key('ArrowDown');
  await key('ArrowRight', 46);
  await page.waitForTimeout(500);
  const searchCount = await page.evaluate(
    () => document.querySelectorAll('[data-section="search-series"] .card').length,
  );
  results.push(`${searchCount > 50 ? 'PASS' : 'FAIL'} 검색 줄 이어 불러오기: 시리즈 카드 ${searchCount}개`);
  await key('Escape');
  await page.waitForTimeout(400);
  await expectFocus('검색에서 돌아오면 검색 버튼', 'search');

  // 6. 라이브러리 → 시리즈 → 뒤로
  for (let i = 0; i < 8 && !(await focused())?.startsWith('library-'); i++) await key('ArrowDown');
  await expectFocus('아래로 → 라이브러리', 'library-L1');
  await key('Enter');
  await page.waitForTimeout(800);
  await expectFocus('라이브러리 진입 → 첫 시리즈', (k) => k?.startsWith('series-'));
  await shot('library');
  await key('ArrowDown', 9);
  const count = await page.evaluate(() => document.querySelectorAll('.grid .card').length);
  results.push(`${count > 50 ? 'PASS' : 'FAIL'} 아래로 내려가면 더 불러오기: 카드 ${count}개`);
  await shot('library-scrolled');
  const opened = await focused();
  await key('Enter');
  await page.waitForTimeout(800);
  await expectFocus('시리즈 상세 → 첫 책', (k) => k?.startsWith('book-'));
  await shot('series');
  // 책 메뉴 (OK 길게, Android T13): 읽음으로 표시 → 알림·진행 새로고침
  const firstBook = await focused();
  await longPress();
  await expectFocus('책 OK 길게 → 메뉴 첫 항목', 'menu-read');
  await shot('book-menu');
  await key('Enter');
  await page.waitForTimeout(800);
  results.push(`${patches.some((p) => /PATCH read-progress \S+ page=24 completed=true/.test(p)) ? 'PASS' : 'FAIL'} 책 메뉴 → 읽음으로 표시 (완독 저장)`);
  await expectText('읽음 알림', '.toast', /읽음으로 표시했습니다/);
  await expectText('진행 새로고침 → 완독', `[data-key="${firstBook}"] .card-subtitle`, /완독/);
  await expectFocus('메뉴가 닫히면 그 책', firstBook);
  await longPress();
  await key('ArrowDown');
  await key('Enter');
  await page.waitForTimeout(800);
  results.push(`${patches.some((p) => p.startsWith('DELETE read-progress')) ? 'PASS' : 'FAIL'} 책 메뉴 → 읽지 않음 (진행 삭제)`);
  await longPress();
  await key('Escape');
  await expectFocus('메뉴 Back → 닫기, 그 책', firstBook);
  await key('Escape');
  await page.waitForTimeout(300);
  await expectFocus('시리즈에서 돌아오면 연 시리즈', opened);
  await key('Escape');
  await page.waitForTimeout(500);
  await expectFocus('라이브러리에서 돌아오면 라이브러리 카드', 'library-L1');
  // 라이브러리 메뉴 (OK 길게, Android T13): 스캔
  await longPress();
  await expectFocus('라이브러리 OK 길게 → 메뉴 첫 항목', 'menu-scan');
  await shot('library-menu');
  await key('Enter');
  await page.waitForTimeout(600);
  results.push(`${patches.some((p) => p === 'POST scan L1') ? 'PASS' : 'FAIL'} 라이브러리 스캔 요청`);
  await expectText('스캔 알림', '.toast', /스캔을 요청했습니다/);
  await expectFocus('메뉴가 닫히면 라이브러리 카드', 'library-L1');

  // 7. 표지 없는 시리즈(S4) → 자리표시 글자
  await key('Enter');
  await page.waitForTimeout(800);
  await shot('library-again');

  // 8. 서버 여러 개 (Android T11): API 키 연결을 두 번째 서버로 더하고, 목록에서 첫 계정으로 바꾼다
  const toSettings = async () => {
    for (let i = 0; i < 12 && !['search', 'settings'].includes(await focused()); i++) await key('ArrowUp');
    if ((await focused()) === 'search') await key('ArrowRight');
    await key('Enter');
    await page.waitForTimeout(400);
  };
  await key('Escape');
  await page.waitForTimeout(800);
  await toSettings();
  await expectFocus('설정 → 목록 맨 위 (지금 서버뿐이라 "다른 서버 추가")', 'server-add');
  await key('Enter');
  await expectFocus('다른 서버 추가 → 주소 칸', 'url');
  const cleared = await page.evaluate(() => document.querySelector('[data-key="url"] input')?.value);
  results.push(`${cleared === '' ? 'PASS' : 'FAIL'} 다른 서버 추가 → 입력칸 비움: "${cleared}"`);
  await typeInto(`http://localhost:${port}/komga`);
  await key('ArrowDown');
  await key('ArrowRight');
  await key('Enter'); // API 키 방식
  await key('ArrowDown');
  await expectFocus('API 키 칸', 'apiKey');
  await typeInto('test');
  await key('ArrowDown');
  await key('ArrowRight');
  await expectFocus('저장 버튼(두 번째 서버)', 'save');
  await key('Enter');
  await page.waitForTimeout(1000);
  await expectFocus('API 키 서버 연결 → 홈', (k) => !!k && k.startsWith('reading-'));
  await toSettings();
  const saved = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.saved-server:not(.builtin)')).map((el) => el.textContent?.replace(/\s+/g, ' ').trim()),
  );
  results.push(`${saved.length === 2 ? 'PASS' : 'FAIL'} 저장된 서버 2개: ${saved.join(' / ')}`);
  const defaultRows = await page.evaluate(() => document.querySelectorAll('.settings .view-row').length);
  results.push(`${defaultRows === 8 ? 'PASS' : 'FAIL'} 서버 설정 "보기 기본값" 8줄 (Android T14): ${defaultRows}`);
  // 앱 이름·15개 언어·만든 사람 소개 (Android 1.0.3~1.0.6)
  const app = await page.evaluate(() => ({
    langs: document.querySelectorAll('[data-section="language"] .button').length,
    about: document.querySelector('.about-box')?.textContent ?? '',
    demo: !!document.querySelector('[data-key="server-demo"]'),
    title: document.title,
  }));
  results.push(`${app.langs === 16 ? 'PASS' : 'FAIL'} 언어 고르기: TV 설정 따름 + 15개 언어 = ${app.langs}`);
  results.push(
    `${/TV Reader for Komga/.test(app.about) && /락로보틱스/.test(app.about) && /한반도의 평화/.test(app.about) && /구글 TV용 버전/.test(app.about) ? 'PASS' : 'FAIL'} 앱 정보: 새 이름·소개·평화·농담`,
  );
  results.push(`${app.demo && app.title === 'TV Reader for Komga' ? 'PASS' : 'FAIL'} 내장 데모 서버 항목, 창 제목 "${app.title}"`);
  const builtin = await page.evaluate(() => ({
    rows: document.querySelectorAll('.saved-server.builtin').length,
    remove: document.querySelectorAll('.saved-server.builtin [data-key^="server-remove"]').length,
    saved: JSON.parse(localStorage.getItem('komgatv.servers') ?? '[]').some((s) => /demo\.komga\.org/.test(s.url)),
  }));
  results.push(`${builtin.rows === 1 && builtin.remove === 0 && !builtin.saved ? 'PASS' : 'FAIL'} 데모 서버는 내장(1줄, 빼기 없음, 목록에 저장 안 함)`);
  await expectFocus('목록 맨 위 = 다른 서버의 바꾸기', 'server-switch-0');
  await shot('saved-servers');
  await key('Enter');
  await page.waitForTimeout(1000);
  const nowKind = await page.evaluate(() => JSON.parse(localStorage.getItem('komgatv.server') ?? '{}').auth?.kind);
  results.push(`${nowKind === 'basic' ? 'PASS' : 'FAIL'} 바꾸기 → 첫 계정(이메일)으로 연결: ${nowKind}`);
  await expectFocus('바꾸기 → 홈부터', (k) => !!k && k.startsWith('reading-'));
  const stack = await page.evaluate(() => document.querySelectorAll('.screen').length);
  results.push(`${stack === 1 ? 'PASS' : 'FAIL'} 바꾸면 쌓인 화면을 버린다: 화면 ${stack}개`);

  // 9. EPUB 리더 (Android T12): 읽는 중 줄 맨 뒤의 EPUB 책 (둘째 장에서 읽던 중)
  await page.waitForTimeout(800);
  for (let i = 0; i < 8 && (await focused()) !== 'reading-book-S7B1'; i++) await key('ArrowRight');
  await expectFocus('EPUB 책 카드', 'reading-book-S7B1');
  await expectText('해시 제목 → 시리즈 이름', '[data-key="reading-book-S7B1"] .card-title', /시리즈 007/);
  await key('Enter');
  await page.waitForTimeout(1500);
  const epub = () =>
    page.evaluate(() => {
      const body = document.querySelector('.epub-content');
      const view = document.querySelector('.epub-viewport');
      return {
        heading: body?.querySelector('h1')?.textContent ?? '',
        scripts: body?.querySelectorAll('script, link, [onclick], a[href]').length ?? -1,
        img: body?.querySelector('img')?.getAttribute('src') ?? '',
        scroll: view?.scrollTop ?? -1,
      };
    });
  const first = await epub();
  results.push(`${first.heading === '둘째 장' ? 'PASS' : 'FAIL'} 저장된 자리(둘째 장)에서 연다: "${first.heading}"`);
  results.push(`${first.scripts === 0 ? 'PASS' : 'FAIL'} 스크립트·외부 CSS·이벤트·링크 제거: ${first.scripts}`);
  results.push(`${first.img.startsWith('blob:') ? 'PASS' : 'FAIL'} 그림을 인증 fetch로: ${first.img.slice(0, 20)}`);
  await shot('epub');
  await key('ArrowDown');
  await page.waitForTimeout(600);
  const scrolled = await epub();
  results.push(`${scrolled.scroll > 500 ? 'PASS' : 'FAIL'} 아래 → 화면 90% 스크롤: ${scrolled.scroll}`);
  await key('Enter');
  await expectText('정보: 3장 중 2장 · 목차 제목', '.reader-overlay-row:nth-child(2)', /3장 중 2장 · 둘째 장 제목/);
  await key('ArrowUp');
  await expectText('정보에서 위 → 글자 크기 110%', '.reader-help', /110%/);
  await shot('epub-overlay');
  // 목차 (Android T13): 정보에서 OK → 목차, 지금 장 줄에 포커스. 목차가 한 줄뿐인 책이라 장마다 한 줄
  await key('Enter');
  await expectFocus('정보에서 OK → 목차의 지금 장 줄', 'toc-1');
  await expectText('목차 줄: 장마다 한 줄', '.toc-panel', /1장.*2장.*3장/s);
  await shot('epub-toc');
  await key('ArrowDown');
  await key('Enter');
  await page.waitForTimeout(800);
  results.push(`${(await epub()).heading === '셋째 장' ? 'PASS' : 'FAIL'} 목차에서 3장 → 셋째 장`);
  await key('ArrowLeft');
  await page.waitForTimeout(800);
  results.push(`${(await epub()).heading === '둘째 장' ? 'PASS' : 'FAIL'} 왼쪽 → 이전 장`);
  await key('ArrowRight');
  await page.waitForTimeout(800);
  results.push(`${(await epub()).heading === '셋째 장' ? 'PASS' : 'FAIL'} 오른쪽 → 다음 장`);
  await key('ArrowUp');
  await page.waitForTimeout(800);
  const back = await epub();
  results.push(`${back.heading === '둘째 장' && back.scroll > 1000 ? 'PASS' : 'FAIL'} 장 처음에서 위 → 이전 장 끝: ${back.heading} ${back.scroll}`);
  await key('ArrowRight');
  await page.waitForTimeout(800);
  await key('Escape');
  await page.waitForTimeout(1200);
  results.push(
    `${patches.some((p) => p.includes('PUT progression S7B1 href=index_split_002.html')) ? 'PASS' : 'FAIL'} 나갈 때 EPUB 자리 저장: ${patches.filter((p) => p.startsWith('PUT')).slice(-1)}`,
  );
  await expectFocus('EPUB에서 돌아오면 그 책', 'reading-book-S7B1');
  await key('Enter');
  await page.waitForTimeout(1500);
  results.push(`${(await epub()).heading === '셋째 장' ? 'PASS' : 'FAIL'} 다시 열면 저장한 장(셋째 장)`);
  await key('Escape');
  await page.waitForTimeout(800);

  // 10. 다음 권으로 이어 읽기 (Android, KMReader처럼): S1B3 끝 → 책 끝 화면 → 다음 권(S1B4) 1쪽
  for (let i = 0; i < 8 && (await focused()) !== 'reading-book-S1B3'; i++) await key('ArrowLeft');
  await expectFocus('S1B3 카드', 'reading-book-S1B3');
  await key('Enter');
  await page.waitForTimeout(1200);
  await key('Enter'); // 정보
  await key('ArrowUp', 3); // 10쪽씩 앞으로 → 끝
  await key('Escape');
  await page.waitForTimeout(400);
  await key('ArrowRight');
  await page.waitForTimeout(800);
  await expectText('마지막 쪽 다음 → 책 끝 화면 (다 읽은 책 › 다음 권, ▶ 안내)', '.end-view', /다 읽음.*03.*›.*다음 권.*04.*▶ \/ OK/s);
  await shot('end-page');
  results.push(`${patches.some((p) => /PATCH read-progress S1B3 page=24 completed=true/.test(p)) ? 'PASS' : 'FAIL'} 마지막 펼침 → 완독 저장`);
  await key('ArrowLeft');
  await page.waitForTimeout(300);
  results.push(`${(await page.evaluate(() => !document.querySelector('.end-view'))) ? 'PASS' : 'FAIL'} 이전 쪽 키 → 마지막 쪽으로`);
  await key('ArrowRight');
  await key('ArrowRight'); // 책 끝에서 한 번 더 = 다음 권
  await page.waitForTimeout(1200);
  await key('Enter');
  await expectText('다음 권 1쪽에서 같은 화면으로', '.reader-overlay-row:nth-child(1)', /04/);
  await expectText('다음 권은 1쪽부터', '.reader-overlay-row:nth-child(2)', /^1 \/ 24쪽|1 \/ \d+ 펼침/);
  await shot('next-book');
  // 11. 이전 권으로 가기 (Android): 04권 첫 펼침에서 뒤로 → 책 첫 화면(이전 권 03 ‹ 지금 권 04) → 한 번 더 → 03권 마지막 쪽
  await key('Escape'); // 정보 닫기
  await key('ArrowLeft');
  await page.waitForTimeout(600);
  // 왼쪽→오른쪽 책: 갈 권(이전 권)은 그리로 가는 ◀ 쪽에 (Android 0.2.4 BoundaryLayout)
  await expectText('첫 펼침에서 뒤로 → 책 첫 화면 (이전 권 ‹ 지금 권, ◀ 안내)', '.end-view', /이전 권.*03.*‹.*지금 권.*04.*◀ \/ OK/s);
  await shot('start-page');
  await key('ArrowLeft');
  await page.waitForTimeout(1200);
  await key('Enter');
  await expectText('한 번 더 → 이전 권(03) 마지막 쪽', '.reader-overlay-row:nth-child(2)', /24 \/ 24쪽|24 \/ 24쪽\)/);
  await expectText('이전 권 제목', '.reader-overlay-row:nth-child(1)', /03/);
  await key('Escape');
  await key('Escape');
  await page.waitForTimeout(800);

  // 12. PDF 책 (S8, 괴수 8호처럼): 원본 쪽에서 JPEG를 꺼내 그린다 — 서버 그림 변환(느림)은 쓰지 않는다
  // 연 책(03)이 다 읽혀 읽는 중 줄에서 빠졌다 — 이 단계는 PDF 시험이라 카드에 바로 포커스를 둔다
  await page.evaluate(() => (document.querySelector('[data-key="reading-book-S8B2"]'))?.focus());
  await expectFocus('PDF 책 카드', 'reading-book-S8B2');
  const before = patches.length;
  await key('Enter');
  await page.waitForTimeout(1200);
  const pdfImg = await page.evaluate(() => document.querySelector('.reader-image')?.getAttribute('src') ?? '');
  const pdfLog = patches.slice(before);
  results.push(`${pdfImg.startsWith('blob:') && pdfLog.some((p) => p === 'RAW S8B2 2') ? 'PASS' : 'FAIL'} PDF 쪽 = 원본에서 꺼낸 JPEG: ${pdfLog.slice(0, 4).join(' | ')}`);
  results.push(`${!pdfLog.some((p) => p.startsWith('RENDER')) ? 'PASS' : 'FAIL'} 서버 그림 변환 안 씀`);
  results.push(`${pdfLog.some((p) => p === 'RAW S8B1 24') ? 'PASS' : 'FAIL'} 처음 근처 → 이전 권 마지막 쪽 미리 받기`);
  // 두 쪽 보기 (이 작품에만)
  await key('Enter');
  await key('Enter');
  await key('ArrowDown');
  await key('ArrowRight');
  await key('Escape');
  await key('Escape');
  await page.waitForTimeout(1000);
  const pdfPages = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.reader-image')).map((img) => (img.getAttribute('src') ?? '').slice(0, 5)),
  );
  results.push(`${pdfPages.length === 2 && pdfPages.every((src) => src === 'blob:') ? 'PASS' : 'FAIL'} PDF 두 쪽 보기: ${pdfPages.join(',')}`);
  // 대비·Lanczos (Android 1.0.1~1.0.2): PDF 쪽은 JPEG라 Lanczos로 다시 맞춰 canvas에 그린다 (가짜 서버 일반 쪽은 SVG)
  await key('Enter');
  await key('Enter');
  await key('ArrowDown', 7);
  await expectFocus('패널: 확대 아래 대비', 'opt-contrast');
  await key('ArrowLeft', 15);
  await expectText('대비: 50%에서 멈춤', '[data-key="opt-contrast"] .view-row-value', /^‹50%›$/);
  await key('ArrowRight', 16);
  await expectText('대비: 5%씩 → 130%', '[data-key="opt-contrast"] .view-row-value', /130%/);
  await key('ArrowDown');
  await expectFocus('패널: 화질 개선', 'opt-enhance');
  await key('ArrowRight', 3);
  await expectText('화질 개선 → Lanczos3', '[data-key="opt-enhance"] .view-row-value', /Lanczos3/);
  await key('Escape');
  await key('Escape');
  await page.waitForTimeout(3000);
  const lanczos = await page.evaluate(() =>
    Array.from(document.querySelectorAll('.reader-canvas')).map((c) => ({
      w: c.width,
      h: c.height,
      shown: !c.classList.contains('pending'),
      filter: c.style.filter,
    })),
  );
  results.push(
    `${lanczos.length === 2 && lanczos.every((c) => c.shown && c.h === 1080) ? 'PASS' : 'FAIL'} Lanczos3: 두 쪽을 그릴 크기로 다시 맞춰 canvas에: ${lanczos.map((c) => `${c.w}x${c.h}`).join(',')}`,
  );
  results.push(`${lanczos.every((c) => /contrast\(1\.3\)/.test(c.filter)) ? 'PASS' : 'FAIL'} 대비 130%: ${lanczos[0]?.filter ?? ''}`);
  const lanczosMode = await page.evaluate(() => document.documentElement.getAttribute('data-lanczos') ?? '');
  results.push(`${lanczosMode.startsWith('workers-') ? 'PASS' : 'FAIL'} Lanczos 계산은 Web Worker로: ${lanczosMode}`);
  await shot('pdf-lanczos-contrast');
  // 넘기면 미리 처리해 둔 다음 펼침이 바로 또렷하게
  await key('ArrowRight');
  await page.waitForTimeout(400);
  const flipped = await page.evaluate(
    () => Array.from(document.querySelectorAll('.reader-layer:last-child .reader-canvas')).filter((c) => !c.classList.contains('pending')).length,
  );
  results.push(`${flipped === 2 ? 'PASS' : 'FAIL'} Lanczos 미리 처리: 넘기고 0.4초 안에 canvas ${flipped}장`);
  await key('ArrowLeft');
  await page.waitForTimeout(400);
  await shot('pdf-double');
  await key('Escape');
  await page.waitForTimeout(800);

  // 13. 표지 없는 EPUB → 제목을 적은 대체 표지 (Android 0.3.0)
  const epubCard = await page.evaluate(() => {
    const card = document.querySelector('[data-key="reading-book-S7B1"]');
    return {
      fallback: card?.querySelector('.card-fallback-title')?.textContent ?? '',
      title: card?.querySelector('.card-title')?.textContent ?? '',
    };
  });
  results.push(`${epubCard.fallback && epubCard.fallback === epubCard.title ? 'PASS' : 'FAIL'} 표지 없는 EPUB: 대체 표지에 제목 "${epubCard.fallback}"`);

  // 14. 읽는 중에서 빼기 (Android 0.3.0~0.3.2): 묻고([유지]에 먼저 포커스) [제거]면 서버 진행을 지우고 두 줄에서 뺀다
  await page.evaluate(() => (document.querySelector('[data-key="reading-book-S8B2"]'))?.focus());
  await expectFocus('PDF 책 카드 (읽는 중)', 'reading-book-S8B2');
  const beforeHide = patches.length;
  await longPress();
  await expectFocus('읽는 중 책 OK 길게 → 묻는 창, [유지]에 포커스', 'confirm-cancel');
  await expectText('묻는 창 질문', '.confirm-question', /읽는 중에서 뺄까요/);
  await shot('reading-hide-confirm');
  await key('Enter'); // 유지
  await page.waitForTimeout(500);
  results.push(`${!(await page.evaluate(() => !!document.querySelector('.confirm-question'))) && patches.length === beforeHide ? 'PASS' : 'FAIL'} [유지] → 닫히고 아무것도 안 지움`);
  await expectFocus('유지하면 그 책 그대로', 'reading-book-S8B2');
  await longPress();
  await key('ArrowRight');
  await expectFocus('[제거]로', 'confirm-ok');
  await key('Enter');
  await page.waitForTimeout(800);
  results.push(`${patches.slice(beforeHide).some((p) => p === 'DELETE read-progress S8B2') ? 'PASS' : 'FAIL'} 빼기 → 서버 진행 지움: ${patches.slice(beforeHide).join(' | ')}`);
  const s8Cards = () =>
    page.evaluate(() => document.querySelectorAll('[data-key^="reading-book-S8"], [data-key^="ondeck-book-S8"]').length);
  results.push(`${(await s8Cards()) === 0 ? 'PASS' : 'FAIL'} 빼면 그 시리즈가 읽는 중·다음 권에서 빠짐`);
  await expectFocus('뺀 뒤 포커스는 같은 줄 카드', (k) => k?.startsWith('reading-book-'));
  await shot('reading-after-hide');
  // 다시 켜도 빠져 있다 (가짜 서버의 다음 권에는 진행을 지운 S8B2가 나오지만 이 TV에서 숨김)
  await page.reload();
  await page.waitForTimeout(2500);
  results.push(`${(await s8Cards()) === 0 ? 'PASS' : 'FAIL'} 다시 켜도 빠진 채`);
} finally {
  console.log(results.join('\n'));
  console.log(`errors: ${errors.length ? errors.join('\n') : 'none'}`);
  await browser.close();
  server.kill();
}
