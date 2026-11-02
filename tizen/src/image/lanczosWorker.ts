// Lanczos Worker — build.mjs가 따로 묶어 앱에 글자로 넣고, lanczosPool이 Blob URL로 띄운다 (file://에서도 되게).
import { lanczosRows } from './lanczos';

interface Job {
  id: number;
  pixels: Uint8ClampedArray;
  width: number;
  height: number;
  newWidth: number;
  newHeight: number;
  a: number;
  y0: number;
  y1: number;
  /** pixels가 시작하는 원본 줄 */
  firstRow: number;
}

const scope = self as unknown as {
  onmessage: ((event: MessageEvent<Job>) => void) | null;
  postMessage: (message: unknown, transfer: Transferable[]) => void;
};

scope.onmessage = (event) => {
  const job = event.data;
  const out = lanczosRows(job.pixels, job.width, job.height, job.newWidth, job.newHeight, job.a, job.y0, job.y1, job.firstRow);
  scope.postMessage({ id: job.id, y0: job.y0, out }, [out.buffer]);
};
