// Kotlin 대조 테스트(pageWorkRef.test.ts)가 쓰는 Node 기능만 — @types/node 를 들이지 않으려고 필요한 것만 적는다.
declare module 'node:fs' {
  export function readdirSync(path: string): string[];
  export function readFileSync(path: string, encoding: 'utf8'): string;
  export function readFileSync(path: string): Uint8Array;
}
declare module 'node:zlib' {
  export function crc32(data: Uint8Array): number;
}
declare const process: { env: Record<string, string | undefined> };
