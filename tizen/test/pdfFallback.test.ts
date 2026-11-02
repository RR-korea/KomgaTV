import { describe, expect, it, vi } from 'vitest';
import type { KomgaClient } from '../src/api/client';
import { loadPage } from '../src/state/session';

describe('PDF 원본 쪽을 못 받으면 서버가 그린 쪽으로 (Android 1.0.1)', () => {
  it('원본 요청 실패 → 일반 쪽', async () => {
    const rendered = new Blob(['x'], { type: 'image/jpeg' });
    const client = {
      fetchRawPage: vi.fn().mockRejectedValue(new Error('500')),
      fetchImage: vi.fn().mockResolvedValue(rendered),
    } as unknown as KomgaClient;
    const blob = await loadPage(client, 'pdf:B1:3', new AbortController().signal);
    expect(blob).toBe(rendered);
    expect((client.fetchImage as ReturnType<typeof vi.fn>).mock.calls[0][0]).toContain('books/B1/pages/3');
  });

  it('취소는 그대로 실패 (일반 쪽을 받지 않는다)', async () => {
    const controller = new AbortController();
    controller.abort();
    const client = {
      fetchRawPage: vi.fn().mockRejectedValue(new Error('aborted')),
      fetchImage: vi.fn(),
    } as unknown as KomgaClient;
    await expect(loadPage(client, 'pdf:B1:3', controller.signal)).rejects.toThrow('aborted');
    expect(client.fetchImage).not.toHaveBeenCalled();
  });
});
