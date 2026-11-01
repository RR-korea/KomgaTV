import { describe, expect, it } from 'vitest';
import { extractSingleJpeg } from '../src/pdf/pdfJpeg';

const jpeg = Uint8Array.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x80, 0x9f, 0xff, 0xd9]);

function bytes(text: string): Uint8Array {
  return Uint8Array.from(text, (c) => c.charCodeAt(0) & 0xff);
}

function concat(...parts: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let at = 0;
  for (const p of parts) {
    out.set(p, at);
    at += p.length;
  }
  return out;
}

/** 한 쪽 PDF (그림 하나) — 실제 Komga raw 쪽과 같은 모양 */
function pdf({
  filter = '[/DCTDecode]',
  width = 800,
  height = 1144,
  media = '[0 0 800 1144]',
  extra = '',
  images = 1,
}: { filter?: string; width?: number; height?: number; media?: string; extra?: string; images?: number } = {}) {
  const parts: Uint8Array[] = [bytes(`%PDF-1.6\n1 0 obj\n<</Type/Page/MediaBox ${media}${extra}>>\nendobj\n`)];
  for (let i = 0; i < images; i++) {
    parts.push(
      bytes(`${5 + i} 0 obj\n<</Type/XObject/Subtype/Image/Width ${width}/Height ${height}/ColorSpace/DeviceRGB/BitsPerComponent 8/Filter ${filter}/Length 9 0 R>>\nstream\r\n`),
      jpeg,
      bytes('\r\nendstream\nendobj\n'),
    );
  }
  parts.push(bytes('%%EOF'));
  return concat(...parts);
}

describe('PDF 한 쪽에서 JPEG 꺼내기', () => {
  it('그림 하나 + DCTDecode면 JPEG 바이트 그대로 (0x80~0x9f 바이트도 위치가 맞다)', () => {
    expect(Array.from(extractSingleJpeg(pdf()) ?? [])).toEqual(Array.from(jpeg));
    expect(extractSingleJpeg(pdf({ filter: '/DCTDecode' }))).not.toBeNull();
  });

  it('다른 압축·그림 여러 개·회전·비율 다름이면 null (서버가 그린 그림으로)', () => {
    expect(extractSingleJpeg(pdf({ filter: '[/FlateDecode /DCTDecode]' }))).toBeNull();
    expect(extractSingleJpeg(pdf({ filter: '/JPXDecode' }))).toBeNull();
    expect(extractSingleJpeg(pdf({ images: 2 }))).toBeNull();
    expect(extractSingleJpeg(pdf({ extra: '/Rotate 90' }))).toBeNull();
    expect(extractSingleJpeg(pdf({ media: '[0 0 1144 800]' }))).toBeNull();
  });

  it('PDF가 아니면 null', () => {
    expect(extractSingleJpeg(bytes('hello'))).toBeNull();
  });
});
