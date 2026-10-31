// Tizen 패키지에 들어갈 dist/ 생성. --serve면 감시 + 로컬 서버(PC 브라우저 개발용).
import * as esbuild from 'esbuild';
import { cpSync, mkdirSync, readFileSync, rmSync } from 'node:fs';

const serve = process.argv.includes('--serve');
const { version } = JSON.parse(readFileSync('package.json', 'utf8'));

rmSync('dist', { recursive: true, force: true });
mkdirSync('dist');
// index.html, config.xml, 아이콘 등 그대로 들어가는 파일
cpSync('static', 'dist', { recursive: true });

const options = {
  entryPoints: { app: 'src/main.tsx' },
  outdir: 'dist',
  bundle: true,
  // file://에서는 module 스크립트가 막히므로 classic 스크립트 하나로 만든다 (PLAN Z1)
  format: 'iife',
  target: 'chrome69',
  jsx: 'automatic',
  jsxImportSource: 'preact',
  minify: !serve,
  sourcemap: serve ? 'inline' : false,
  legalComments: 'none',
  define: { APP_VERSION: JSON.stringify(version) },
  logLevel: 'info',
};

if (serve) {
  const ctx = await esbuild.context(options);
  await ctx.watch();
  const { port } = await ctx.serve({ servedir: 'dist', port: 5173 });
  console.log(`http://localhost:${port}/`);
} else {
  await esbuild.build(options);
}
