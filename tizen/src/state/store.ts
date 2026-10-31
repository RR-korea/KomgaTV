// 아주 작은 관찰 가능한 상태 저장소 + Preact 훅. 외부 라이브러리 없이 Compose의 StateFlow 역할.
import { useEffect, useState } from 'preact/hooks';

export class Store<T> {
  private listeners = new Set<(value: T) => void>();

  constructor(private value: T) {}

  get(): T {
    return this.value;
  }

  set(next: T): void {
    if (Object.is(next, this.value)) return;
    this.value = next;
    this.listeners.forEach((listener) => listener(next));
  }

  update(patch: Partial<T>): void {
    this.set({ ...this.value, ...patch });
  }

  subscribe(listener: (value: T) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
}

export function useStore<T>(store: Store<T>): T {
  const [value, setValue] = useState(store.get());
  useEffect(() => {
    setValue(store.get());
    return store.subscribe(setValue);
  }, [store]);
  return value;
}
