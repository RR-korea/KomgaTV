// Android(Kotlin) 결과와 실제 쪽 비교 — 평소에는 건너뛴다.
// TIZEN_REF_IN(raw 쪽 폴더)·TIZEN_REF_KOTLIN(Kotlin 이 쓴 결과 표)이 있을 때만 돈다. 방법은 docs/PLAN.md "Kotlin 대조".
import { readdirSync, readFileSync } from 'node:fs';
import { crc32 } from 'node:zlib';
import { describe, expect, it } from 'vitest';
import { findBorders } from '../src/image/cropBorders';
import { collapseGutters } from '../src/image/gutters';
import { processPage } from '../src/image/pageWork';

const dir = process.env.TIZEN_REF_IN;
const kotlin = process.env.TIZEN_REF_KOTLIN;

const crc = (pixels: Int32Array) => crc32(new Uint8Array(pixels.buffer, pixels.byteOffset, pixels.byteLength));

describe.skipIf(!dir || !kotlin)('Kotlin 대조 (실제 쪽)', () => {
  it('여백 잘라내기·칸 간격 없애기 결과가 Kotlin 과 같다', () => {
    const expected = new Map<string, string>();
    for (const line of readFileSync(kotlin as string, 'utf8').split('\n')) {
      if (line.trim()) expected.set(line.split('\t')[0], line.trim());
    }
    const different: string[] = [];
    let compared = 0;
    for (const file of readdirSync(dir as string).filter((name) => name.endsWith('.bin')).sort()) {
      const name = file.replace(/\.bin$/, '');
      const want = expected.get(name);
      if (!want) continue;
      const bytes = readFileSync(`${dir}/${file}`);
      const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
      const w = view.getInt32(0, true);
      const h = view.getInt32(4, true);
      const px = new Int32Array(bytes.buffer.slice(bytes.byteOffset + 8, bytes.byteOffset + 8 + w * h * 4));
      const b = (tb: boolean, lr: boolean, blank = false) => findBorders(px, w, h, tb, lr, blank)?.join(',') ?? 'null';
      const gut = collapseGutters(px, w, h);
      const full = processPage({ pixels: px, width: w, height: h }, 'ALL', true);
      const got = [
        name,
        w,
        h,
        b(true, true),
        b(true, false),
        b(false, true),
        b(true, true, true),
        gut ? crc(gut) : 'null',
        full.width,
        full.height,
        crc(full.pixels),
      ].join('\t');
      compared++;
      if (got !== want) different.push(`${want}\n  ≠ ${got}`);
    }
    console.log(`Kotlin 대조: ${compared}쪽, 다른 쪽 ${different.length}`);
    expect(different, different.slice(0, 10).join('\n')).toEqual([]);
    expect(compared).toBeGreaterThan(0);
  }, 600000);
});
