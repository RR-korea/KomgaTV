// 서버 연결 실패 원인. Android의 ServerConnectionException과 같은 구분이되,
// 브라우저 fetch는 DNS·연결 거부·SSL·CORS 실패를 모두 TypeError 하나로 숨기므로 Unreachable에 묶는다.

export type ConnectionErrorKind = 'invalidUrl' | 'unreachable' | 'unauthorized' | 'other';

const defaultMessages: Record<ConnectionErrorKind, string> = {
  invalidUrl: '서버 주소 형식이 올바르지 않습니다.',
  unreachable: '서버에 연결할 수 없습니다. 주소, 네트워크, 인증서(자체 서명은 미지원)를 확인하세요.',
  unauthorized: '인증에 실패했습니다 (401). 이메일·비밀번호 또는 API 키를 확인하세요.',
  other: '서버 연결 중 오류가 발생했습니다.',
};

export class ConnectionError extends Error {
  readonly kind: ConnectionErrorKind;
  /** HTTP 응답이 있었으면 그 상태 코드 */
  readonly status?: number;

  constructor(kind: ConnectionErrorKind, options: { message?: string; status?: number } = {}) {
    super(options.message ?? defaultMessages[kind]);
    this.name = 'ConnectionError';
    this.kind = kind;
    this.status = options.status;
  }
}

/** HTTP 오류 응답 (연결 자체는 됨) */
export class HttpError extends Error {
  readonly status: number;

  constructor(status: number, url: string) {
    super(`HTTP ${status}: ${url}`);
    this.name = 'HttpError';
    this.status = status;
  }
}

/** API 호출 중 난 예외를 연결 실패 원인으로 분류한다. */
export function classifyError(error: unknown): ConnectionError {
  if (error instanceof ConnectionError) return error;
  if (error instanceof HttpError) {
    return error.status === 401
      ? new ConnectionError('unauthorized', { status: 401 })
      : new ConnectionError('other', { status: error.status, message: `서버 오류 (HTTP ${error.status})` });
  }
  // fetch 네트워크 실패(TypeError)와 시간 초과(AbortError)
  if (error instanceof TypeError || (error instanceof Error && error.name === 'AbortError')) {
    return new ConnectionError('unreachable');
  }
  return new ConnectionError('other');
}
