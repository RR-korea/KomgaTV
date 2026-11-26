// 쪽 그림 처리(여백 잘라내기·칸 간격 없애기) 속도 재기 — PC(Node)에서. TV는 이보다 몇 배 느리다.
// 실행: node dev/pagework-bench.mjs <raw 쪽 폴더> [쪽 수]
// raw 쪽: 너비·높이(int32 LE) 뒤에 ARGB int32 LE 픽셀 (docs/PLAN.md "Kotlin 대조"의 표본과 같은 형식)
import * as esbuild from 'esbuild';
import { readdirSync, readFileSync } from 'node:fs';

const dir = process.argv[2];
const limit = Number(process.argv[3] ?? 60);
if (!dir) {
  console.error('사용법: node dev/pagework-bench.mjs <raw 쪽 폴더> [쪽 수]');
  process.exit(1);
}
const built = await esbuild.build({
  entryPoints: ['src/image/pageWork.ts'],
  bundle: true,
  write: false,
  format: 'esm',
  target: 'chrome69',
});
const { processPage } = await import(`data:text/javascript;base64,${Buffer.from(built.outputFiles[0].text).toString('base64')}`);

const files = readdirSync(dir).filter((name) => name.endsWith('.bin')).sort().slice(0, limit);
const times = [];
for (const file of files) {
  const bytes = readFileSync(`${dir}/${file}`);
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const width = view.getInt32(0, true);
  const height = view.getInt32(4, true);
  const pixels = new Int32Array(bytes.buffer.slice(bytes.byteOffset + 8, bytes.byteOffset + 8 + width * height * 4));
  const started = performance.now();
  processPage({ pixels, width, height }, 'ALL', true);
  times.push({ file, ms: performance.now() - started, size: `${width}x${height}` });
}
times.sort((a, b) => a.ms - b.ms);
const total = times.reduce((sum, t) => sum + t.ms, 0);
const at = (q) => times[Math.min(times.length - 1, Math.floor(times.length * q))];
console.log(`쪽 ${times.length}장: 평균 ${(total / times.length).toFixed(0)}ms, 중앙 ${at(0.5).ms.toFixed(0)}ms, 90% ${at(0.9).ms.toFixed(0)}ms`);
console.log(`가장 느린 쪽: ${times.slice(-3).map((t) => `${t.file} ${t.size} ${t.ms.toFixed(0)}ms`).join(', ')}`);
