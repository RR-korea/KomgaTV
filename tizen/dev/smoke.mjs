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
  for (const line of text.split('\n')) if (line.startsWith('PATCH')) patches.push(line.trim());
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
  await expectText('RTL: 왼쪽 → 다음 펼침 (12→13)', '.reader-overlay-row:nth-child(2)', /13/);
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
  await expectText('패널: 세로 스크롤', '[data-key="opt-type"] .panel-option-value', /세로 스크롤/);
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
  // 두 쪽 보기 상태에서 바꿨으므로 [2,3] 펼침의 첫 쪽(2쪽)에서 시작 → 오른쪽 두 번이면 4쪽 맨 위
  await key('ArrowRight', 2);
  await page.waitForTimeout(400);
  await key('Enter');
  await expectText('오른쪽 ×2 → 4쪽', '.reader-overlay-row:nth-child(2)', /4 \/ 24/);
  await shot('continuous-overlay');
  await key('Escape');
  await key('Escape');
  await page.waitForTimeout(1000);
  results.push(`${patches.some((p) => p.includes('S5B2 page=4')) ? 'PASS' : 'FAIL'} 세로 스크롤 진행 저장 4쪽: ${patches.filter((p) => p.includes('S5B2')).join(' | ')}`);
  await expectFocus('세로 스크롤 책에서 돌아오면 그 책', 'reading-book-S5B2');
  await page.waitForTimeout(1500); // 돌아온 뒤 조용한 새로고침이 끝나기를 기다린다

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
  await key('Escape');
  await page.waitForTimeout(300);
  await expectFocus('시리즈에서 돌아오면 연 시리즈', opened);
  await key('Escape');
  await page.waitForTimeout(500);
  await expectFocus('라이브러리에서 돌아오면 라이브러리 카드', 'library-L1');

  // 7. 표지 없는 시리즈(S4) → 자리표시 글자
  await key('Enter');
  await page.waitForTimeout(800);
  await shot('library-again');
} finally {
  console.log(results.join('\n'));
  console.log(`errors: ${errors.length ? errors.join('\n') : 'none'}`);
  await browser.close();
  server.kill();
}
