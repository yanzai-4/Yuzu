import type { ApiError, ErrorCode } from './types';
import { formatFullTime } from '../lib/time';

/** v0.0.4 🍊 HTTP verbs used by the REST contract. */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

/** v0.0.4 🍊 Error thrown for every failed request; carries the backend's ApiError body. */
export class ApiRequestError extends Error {
  readonly apiError: ApiError;
  readonly status: number;
  readonly method: HttpMethod;
  readonly path: string;

  /** v0.0.4 🍊 Wraps an ApiError with the request that produced it. */
  constructor(apiError: ApiError, status: number, method: HttpMethod, path: string) {
    super(`${apiError.code}: ${apiError.message}`);
    this.name = 'ApiRequestError';
    this.apiError = apiError;
    this.status = status;
    this.method = method;
    this.path = path;
  }

  /** v0.0.4 🍊 The stable error code the UI switches on. */
  get code(): ErrorCode {
    return this.apiError.code;
  }
}

/** v0.0.4 🍊 Options of a single request. */
export interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
  query?: Record<string, string | number | boolean | null | undefined>;
  signal?: AbortSignal;
  /** Do not toast / log a failure (the caller handles it). */
  silent?: boolean;
}

type ErrorListener = (error: ApiRequestError) => void;
const listeners = new Set<ErrorListener>();

/** v0.0.4 🍊 Registers a listener told about every failed request (toast + trace log); returns an unsubscribe. */
export function onRequestError(listener: ErrorListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** v0.0.4 🍊 Builds an ApiRequestError, notifies listeners unless silent, and returns it for throwing. */
export function failRequest(
  apiError: ApiError,
  status: number,
  method: HttpMethod,
  path: string,
  silent = false,
): ApiRequestError {
  const error = new ApiRequestError(apiError, status, method, path);
  if (!silent) listeners.forEach((listener) => listener(error));
  return error;
}

/** v0.0.4 🍊 A locally synthesized ApiError (network failures, malformed responses). */
export function localApiError(code: ErrorCode, message: string, details: Record<string, unknown> = {}): ApiError {
  return { code, message, details, agentId: null, time: formatFullTime(new Date()) };
}

/** v0.0.4 🍊 Performs a JSON request against the backend and resolves with the typed body. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const url = withQuery(path, options.query);
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    throw failRequest(
      localApiError('INTERNAL', 'Cannot reach the Yuzu server. Is the backend running?', { url }),
      0,
      method,
      path,
      options.silent,
    );
  }

  const text = response.status === 204 ? '' : await response.text();
  const body = parseJson(text);
  if (!response.ok || (response.status === 202 && isApiError(body))) {
    const apiError = isApiError(body)
      ? { ...body, details: body.details ?? {} }
      : localApiError(response.status === 404 ? 'NOT_FOUND' : 'INTERNAL', `Request failed with HTTP ${response.status}.`, {
          url,
          status: response.status,
        });
    throw failRequest(apiError, response.status, method, path, options.silent);
  }
  return body as T;
}

/** v0.0.4 🍊 True when a value has the shape of the backend ApiError body. */
export function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ApiError).code === 'string' &&
    typeof (value as ApiError).message === 'string'
  );
}

function parseJson(text: string): unknown {
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function withQuery(path: string, query: RequestOptions['query']): string {
  if (!query) return path;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
  }
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}
