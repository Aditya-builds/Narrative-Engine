export function messageFromHttpError(err: unknown, fallback: string): string {
  const http = err as {
    name?: string;
    status?: number;
    message?: string;
    error?: { detail?: unknown; error?: string; message?: string };
  };
  if (http?.name === 'TimeoutError') {
    return 'That request took too long. Try again.';
  }
  if (http?.status === 0) {
    return 'Cannot reach the agent. Is FastAPI running on port 8000?';
  }
  const detail = http?.error?.detail;
  if (typeof detail === 'string' && _safeDetail(detail)) {
    return detail;
  }
  if (Array.isArray(detail)) {
    const first = (detail[0] as { msg?: string } | undefined)?.msg;
    if (typeof first === 'string' && _safeDetail(first)) {
      return first;
    }
  }
  if (typeof http?.error?.message === 'string' && _safeDetail(http.error.message)) {
    return http.error.message;
  }
  if (typeof http?.error?.error === 'string' && _safeDetail(http.error.error)) {
    return http.error.error;
  }
  if (http?.status === 401) {
    return typeof detail === 'string' && _safeDetail(detail)
      ? detail
      : 'No OpenAI API key is available. Paste your own key, or add OPENAI_API_KEY to the agent .env.';
  }
  if (http?.status === 404) {
    return 'That page or character could not be found.';
  }
  if (http?.status === 429) {
    return 'Too many replies at once. Wait a few seconds and try again.';
  }
  return fallback;
}

function _safeDetail(text: string): boolean {
  const trimmed = text.trim();
  return trimmed.length > 0 && trimmed.length < 240 && !trimmed.includes('Traceback');
}
