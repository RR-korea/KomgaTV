// 삼성 스토어용 스크린샷 (1920×1080 JPEG): 빌드한 앱(dist/index.html)을 PC Edge로 열어 Komga 공식 데모 서버에 연결해 찍는다.
// 개인 서버의 책은 쓰지 않는다. TV처럼 file://에서 열고, 교차 출처는 브라우저 옵션으로 푼다(TV 앱은 config.xml <access>).
// 읽은 위치를 바꾸지 않게 쪽을 넘기지 않는다 (보기 설정은 이 브라우저에만 저장).
//
//   npm run build && node dev/store-shots.mjs ko   → store/screenshots/ko/
//   node dev/store-shots.mjs en                    → store/screenshots/en/
import { mkdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { chromium } from 'playwright-core';

const lang = process.argv[2] ?? 'en';
const out = join('store/screenshots', lang);
mkdirSync(out, { recursive: true });
const DEMO = { url: 'https://demo.komga.org', auth: { kind: 'basic', email: 'demo@komga.org', password: 'komga-demo' } };

const browser = await chromium.launch({
  channel: 'msedge',
  headless: true,
  args: ['--disable-web-security', '--allow-file-access-from-files'],
});
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, locale: lang === 'ko' ? 'ko-KR' : 'en-US' });
const app = pathToFileURL(resolve('dist/index.html')).href;
const wait = (ms) => page.waitForTimeout(ms);
const key = async (name, times = 1) => {
  for (let i = 0; i < times; i++) {
    await page.keyboard.press(name);
    await wait(250);
  }
};
const shot = async (name) => {
  await wait(800);
  await page.screenshot({ path: join(out, `${name}.jpg`), type: 'jpeg', quality: 92 });
  console.log('shot', name);
};
/** 제목에 text가 든 카드에 포커스 */
const focusCard = async (text, prefix = '') =>
  page.evaluate(
    ({ text, prefix }) => {
      const card = Array.from(document.querySelectorAll(`[data-key^="${prefix}"].card`)).find((el) =>
        (el.querySelector('.card-title')?.textContent ?? '').includes(text),
      );
      card?.focus();
      return card?.getAttribute('data-key') ?? null;
    },
    { text, prefix },
  );

try {
  await page.goto(app);
  await page.evaluate(
    ({ demo, lang }) => {
      localStorage.clear();
      localStorage.setItem('komgatv.server', JSON.stringify(demo));
      localStorage.setItem('komgatv.servers', JSON.stringify([demo]));
      localStorage.setItem('komgatv.language', lang);
    },
    { demo: DEMO, lang },
  );
  await page.reload();
  await wait(6000);
  await shot('01_home');

  // 시리즈: 최근 추가된 시리즈 줄의 만화
  const series = (await focusCard('Space Adventures', 'new-series-')) ?? (await focusCard('', 'new-series-'));
  console.log('series', series);
  await key('Enter');
  await wait(4000);
  await shot('02_series');
  await key('Escape');
  await wait(1500);

  // 만화 한 쪽 (읽는 중 줄의 만화 — 읽던 쪽에서 연다)
  const comic = (await focusCard('Space Adventures', 'reading-')) ?? (await focusCard('Super Duck', 'reading-'));
  console.log('comic', comic);
  await key('Enter');
  await wait(4000);
  await shot('03_reader');
  await key('Enter'); // 정보
  await wait(600);
  await shot('04_overlay');
  // 보기 설정: 두 쪽, 대비 110%, Lanczos3
  await key('Enter');
  await wait(400);
  await key('ArrowDown');
  await key('ArrowRight');
  await key('ArrowDown', 6);
  await key('ArrowRight', 2);
  await key('ArrowDown');
  await key('ArrowRight', 3);
  await shot('05_view_settings');
  await key('Escape');
  await key('Escape');
  await wait(3000);
  await shot('06_two_page');
  await key('Escape');
  await wait(1500);

  // EPUB
  const epub = (await focusCard('Moby', 'reading-')) ?? (await focusCard('Alice', 'reading-'));
  console.log('epub', epub);
  await key('Enter');
  await wait(5000);
  await shot('07_epub');
  await key('Escape');
  await wait(1500);

  // 검색
  await page.evaluate(() => document.querySelector('[data-key="search"]')?.focus());
  await key('Enter');
  await wait(800);
  await key('Enter');
  await page.keyboard.type('Space');
  await key('Enter');
  await wait(3500);
  await key('ArrowDown');
  await shot('08_search');
} finally {
  await browser.close();
}
