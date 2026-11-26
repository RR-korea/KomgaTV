// 쪽 그림 처리 Worker (여백 잘라내기·칸 간격 없애기) — build.mjs가 따로 묶어 앱에 글자로 넣고,
// pageWorkPool이 Blob URL로 띄운다 (file://에서도 되게). 쪽 전체를 훑는 무거운 계산이라 화면 스레드 밖에서 한다.
import { argbToRgba, type CropBorders, processPage, rgbaToArgb } from './pageWork';

interface Job {
  id: number;
  /** 캔버스 RGBA 바이트 */
  rgba: Uint8ClampedArray;
  width: number;
  height: number;
  crop: CropBorders;
  removeGutters: boolean;
}

const scope = self as unknown as {
  onmessage: ((event: MessageEvent<Job>) => void) | null;
  postMessage: (message: unknown, transfer: Transferable[]) => void;
};

scope.onmessage = (event) => {
  const job = event.data;
  const page = { pixels: rgbaToArgb(job.rgba), width: job.width, height: job.height };
  const done = processPage(page, job.crop, job.removeGutters);
  const rgba = argbToRgba(done.pixels);
  scope.postMessage({ id: job.id, rgba, width: done.width, height: done.height }, [rgba.buffer]);
};
