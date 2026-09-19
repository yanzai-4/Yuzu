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

function humanTitle(error: ApiError): string {
  switch (error.code) {
    case 'AGENT_LIMIT':
      return 'The office is full';
    case 'NOT_CONFIGURED':
      return 'Not configured yet';
    case 'LLM_AUTH':
      return 'API key rejected';
    case 'LLM_TRANSPORT':
      return 'Model provider unreachable';
    case 'PERMISSION_DENIED':
      return 'Permission denied';
    case 'SECURITY_BLOCKED':
      return 'Blocked by safety review';
    case 'APPROVAL_REQUIRED':
      return 'Approval required';
    default:
      return 'Something went wrong';
  }
}
