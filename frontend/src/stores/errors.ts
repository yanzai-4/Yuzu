import type { ApiError } from '../api/types';
import { addErrorEntry, type ErrorEntry } from './trace';
import { pushToast } from './ui';

let errorSeq = 0;

/** v0.0.4 🍊 A fresh error-log entry for an ApiError. */
export function errorEntry(error: ApiError, source: ErrorEntry['source'], context: string | null): ErrorEntry {
  return { key: `err-${++errorSeq}`, source, context, error: { ...error, details: error.details ?? {} } };
}

/** v0.0.4 🍊 Shows an error toast (code + message). */
export function toastError(error: ApiError): void {
  pushToast({ tone: 'error', title: humanTitle(error), message: error.message, code: error.code });
}

/** v0.0.4 🍊 Reports a failure: toast + Trace tab error log. */
export function reportError(error: ApiError, source: ErrorEntry['source'], context: string | null = null): void {
  addErrorEntry(errorEntry(error, source, context));
  toastError(error);
}

const TITLES: Partial<Record<ApiError['code'], string>> = {
  BAD_REQUEST: 'Invalid request',
  NOT_FOUND: 'Not found',
  CONFLICT: 'Conflict',
  AGENT_LIMIT: 'The office is full',
  PERMISSION_DENIED: 'Permission denied',
  SANDBOX_VIOLATION: 'Sandbox violation',
  SECURITY_BLOCKED: 'Blocked by safety review',
  APPROVAL_REQUIRED: 'Approval required',
  NOT_CONFIGURED: 'Not configured yet',
  LLM_AUTH: 'API key rejected',
  LLM_TRANSPORT: 'Model provider unreachable',
  LLM_OUTPUT_INVALID: 'Model output invalid',
  TOOL_EXECUTION: 'A tool failed',
  CANCELLED: 'Cancelled',
};

function humanTitle(error: ApiError): string {
  return TITLES[error.code] ?? 'Something went wrong';
}
