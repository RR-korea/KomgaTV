// Komga 검색 조건 JSON (POST /series/list, /books/list 본문의 condition).
import type { ReadStatus } from './types';

export type Condition = Record<string, unknown>;

const is = (value: string) => ({ operator: 'is', value });

export const libraryIs = (id: string): Condition => ({ libraryId: is(id) });
export const seriesIs = (id: string): Condition => ({ seriesId: is(id) });
export const readStatusIs = (status: ReadStatus): Condition => ({ readStatus: is(status) });
export const allOf = (...conditions: Condition[]): Condition => ({ allOf: conditions });

export interface SearchBody {
  condition?: Condition;
  fullTextSearch?: string;
}
