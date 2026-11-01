// PDF 한 쪽에서 JPEG 그림을 그대로 꺼낸다 (Android의 "PDF 쪽을 원래 모양(raw)으로 받아 TV에서 그리기"에 해당).
// 스캔한 만화 PDF의 한 쪽은 대개 DCTDecode(JPEG) 그림 하나를 쪽 전체에 놓은 것이다. 그 JPEG 바이트를 꺼내면
// 브라우저가 바로 그린다 — Komga가 쪽을 그림으로 바꿔 주는 것(쪽당 2~6초)보다 훨씬 빠르다 (원본 0.2초).
// 모양이 다르면(그림이 여러 개, 다른 압축, 회전, 쪽과 그림 비율이 다름, CMYK·Decode) null → 서버가 그린 그림을 쓴다.

/** 바이트 = 글자 하나로 읽는다 (windows-1252는 모든 바이트를 한 글자로 바꾼다 — 위치가 바이트 위치와 같다) */
function asText(bytes: Uint8Array): string {
  return new TextDecoder('windows-1252').decode(bytes);
}

function numbers(text: string): number[] {
  return (text.match(/-?\d+(\.\d+)?/g) ?? []).map(Number);
}

export function extractSingleJpeg(bytes: Uint8Array): Uint8Array | null {
  const s = asText(bytes);
  // String.prototype.matchAll은 Chrome 73부터 — 2020년형(Chrome 69)을 위해 exec로 센다
  const imagePattern = /\/Subtype\s*\/Image\b/g;
  let at = -1;
  let count = 0;
  for (let found = imagePattern.exec(s); found; found = imagePattern.exec(s)) {
    at = found.index;
    count++;
  }
  if (count !== 1) return null;

  // 그림 객체의 사전: 앞쪽 "obj"부터 뒤쪽 "stream"까지
  const objStart = s.lastIndexOf('obj', at);
  const streamAt = s.indexOf('stream', at);
  if (objStart < 0 || streamAt < 0) return null;
  const dict = s.slice(objStart, streamAt);
  const filter = dict.match(/\/Filter\s*(\[[^\]]*\]|\/\w+)/);
  if (!filter || filter[1].replace(/[[\]\s]/g, '') !== '/DCTDecode') return null;
  if (/\/Decode\s*\[/.test(dict) || /DeviceCMYK|\/Indexed/.test(dict) || /\/SMask\b|\/Mask\b/.test(dict)) return null;

  // 회전된 쪽이나 쪽과 비율이 다른 그림은 서버에 맡긴다
  if (/\/Rotate\s+(90|180|270|-90)/.test(s)) return null;
  const width = Number(dict.match(/\/Width\s+(\d+)/)?.[1]);
  const height = Number(dict.match(/\/Height\s+(\d+)/)?.[1]);
  const box = s.match(/\/MediaBox\s*\[([^\]]*)\]/);
  if (box && width && height) {
    const [x0, y0, x1, y1] = numbers(box[1]);
    const pageRatio = Math.abs(x1 - x0) / Math.abs(y1 - y0);
    if (!Number.isFinite(pageRatio) || Math.abs(pageRatio / (width / height) - 1) > 0.03) return null;
  }

  // 스트림 자료: "stream" 다음 줄바꿈 뒤부터, JPEG 시작(FFD8)과 끝(FFD9)
  let start = streamAt + 'stream'.length;
  if (s[start] === '\r') start++;
  if (s[start] === '\n') start++;
  const end = s.indexOf('endstream', start);
  if (end < 0) return null;
  const eoi = s.lastIndexOf('ÿÙ', end);
  if (bytes[start] !== 0xff || bytes[start + 1] !== 0xd8 || eoi < start) return null;
  return bytes.subarray(start, eoi + 2);
}
