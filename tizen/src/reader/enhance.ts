// 화질 개선·대비를 그리기 설정으로 (Android ImageEnhancer).
import type { ImageEnhancement } from '../state/readerPrefs';

/** Lanczos 창 크기 (CPU, 화면에 그릴 크기로 맞춘다). null이면 하지 않는다 */
export function lanczosWindow(enhancement: ImageEnhancement): number | null {
  switch (enhancement) {
    case 'LANCZOS2':
      return 2;
    case 'LANCZOS':
      return 3;
    case 'LANCZOS4':
      return 4;
    default:
      return null;
  }
}

/**
 * 쪽 그림에 걸 CSS filter. 선명하게는 SVG 언샤프 필터(App.tsx), 대비는 contrast() —
 * 가운데 회색 기준 (v − 0.5) × c + 0.5 로 Android 색 행렬(t = 128 × (1 − c))과 같은 식, GPU가 한다.
 */
export function imageFilter(enhancement: ImageEnhancement, contrastPercent: number): string | undefined {
  const parts: string[] = [];
  if (enhancement === 'SHARPEN') parts.push('url(#ktv-sharpen)');
  if (enhancement === 'SHARPEN_STRONG') parts.push('url(#ktv-sharpen-strong)');
  if (contrastPercent !== 100) parts.push(`contrast(${contrastPercent / 100})`);
  return parts.length ? parts.join(' ') : undefined;
}
