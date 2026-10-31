// 삼성 계정 로그인 → 인증서 발급용 access token 받기 (Tizen Studio Certificate Manager와 같은 clientId·콜백).
// 실행: SA_EMAIL=... SA_PASSWORD=... node dev/samsung-cert/login.mjs <token.json 경로(저장소 밖)>
// Edge 창이 열린다. 2차 인증(문자)은 그 창에서 사람이 마친다.
// 계정 정보는 환경 변수로만 받고 어디에도 저장하지 않는다. 토큰(수명 짧음)만 지정한 파일에 둔다.
import { createServer } from 'node:http';
import { writeFileSync } from 'node:fs';
import { chromium } from 'playwright-core';

const PORT = 4794;
const CALLBACK = `http://localhost:${PORT}/signin/callback`;
// Tizen Studio cert-add-on(SigninDialog)이 여는 주소 그대로
const LOGIN_URL = `https://account.samsung.com/mobile/account/check.do?serviceID=v285zxnl3h&actionID=StartOAuth2&accessToken=Y&redirect_uri=${CALLBACK}`;

let resolveToken;
const tokenPromise = new Promise((resolve) => (resolveToken = resolve));

const server = createServer((req, res) => {
  let body = '';
  req.on('data', (chunk) => (body += chunk));
  req.on('end', () => {
    const url = new URL(req.url, CALLBACK);
    const params = new URLSearchParams(body);
    const fields = {};
    for (const [k, v] of url.searchParams) fields[k] = v;
    for (const [k, v] of params) fields[k] = v;
    console.log(`callback ${req.method} ${url.pathname} fields=${Object.keys(fields).join(',')}`);
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end('<h1>OK</h1>');
    if (Object.keys(fields).length) resolveToken(fields);
  });
}).listen(PORT, '127.0.0.1');

const browser = await chromium.launch({ channel: 'msedge', headless: false });
const page = await browser.newPage({ locale: 'ko-KR' });
try {
  await page.goto(LOGIN_URL, { waitUntil: 'domcontentloaded' });
  const email = page.locator('input[type="email"], input#iptLgnPlnID, input[name="iptLgnPlnID"]').first();
  await email.waitFor({ timeout: 30000 });
  await email.fill(process.env.SA_EMAIL);
  await email.press('Enter');
  const password = page.locator('input[type="password"]').first();
  await password.waitFor({ timeout: 30000 });
  await password.fill(process.env.SA_PASSWORD);
  await password.press('Enter');
  console.log('submitted; waiting for callback (finish any extra verification in the window)');
  const fields = await Promise.race([
    tokenPromise,
    new Promise((_, reject) => setTimeout(() => reject(new Error('timeout waiting for callback')), 180000)),
  ]);
  // 콜백 값 중 code에 JSON(access_token, userId 등)이 들어 있는 경우가 있다
  let info = fields;
  for (const value of Object.values(fields)) {
    try {
      const parsed = JSON.parse(value);
      if (parsed && typeof parsed === 'object') info = { ...info, ...parsed };
    } catch {
      // JSON 아님
    }
  }
  writeFileSync(process.argv[2] ?? 'token.json', JSON.stringify(info));
  console.log(`token keys: ${Object.keys(info).join(',')}`);
} catch (error) {
  await page.screenshot({ path: 'login-error.png' });
  console.log(`url at error: ${page.url()}`);
  throw error;
} finally {
  await browser.close();
  server.close();
}
