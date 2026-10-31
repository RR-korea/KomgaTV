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
  await expectFocus('홈 진입 → 이어 읽기 첫 책', 'book-S1B3');
  await shot('home');
  await key('ArrowRight');
  await expectFocus('오른쪽 → 둘째 책', 'book-S2B1');
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
  await expectFocus('한 번 더 OK → 패널 첫 항목', 'opt-layout');
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
  await shot('reader-double-9-10');
  await key('Enter');
  await expectText('두 쪽 보기 → 9,10쪽 펼침', '.reader-overlay-row:nth-child(2)', /9, 10 \/ 24/);
  await key('Escape');
  await key('Escape'); // 나가기 → 즉시 저장
  await page.waitForTimeout(1200);

  // 4. 홈 복귀: 새로고침 + 연 책 포커스
  await expectFocus('리더에서 돌아오면 연 책', 'book-S1B3');
  results.push(`${patches.some((p) => p.includes('S1B3 page=10')) ? 'PASS' : 'FAIL'} 나갈 때 진행 저장 (보이는 쪽 중 큰 번호 10): ${patches.join(' | ')}`);
  await expectText('홈 진행도 갱신', '[data-key="book-S1B3"] .card-subtitle', /10\/24/);
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
  await expectFocus('RTL 책에서 돌아오면 그 책', 'book-S2B1');

  // 6. 라이브러리 → 시리즈 → 뒤로
  await key('ArrowDown');
  await expectFocus('아래 → 라이브러리', 'library-L1');
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
