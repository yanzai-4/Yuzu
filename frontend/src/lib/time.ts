/**
 * v0.0.4 🍊 Natural-language time helpers.
 *
 * The backend never sends epoch numbers: times look like "Sat Sep 19, 11:32:05 AM" (compact) or
 * "Saturday, September 19, 2026 at 11:32:05 AM PDT" (full). These helpers extract display pieces and
 * a sortable number; ordering of live data should still prefer `seq` / event ids.
 */

const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];
const WEEKDAYS_SHORT = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const WEEKDAYS_LONG = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONTHS_LONG = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

const DATE_TIME =
  /\b([A-Za-z]{3})[a-z]*\.? (\d{1,2}),?(?: (\d{4}))?(?: at|,)? (\d{1,2}):(\d{2})(?::(\d{2}))? ?([AP]M)\b/i;
const CLOCK = /(\d{1,2}):(\d{2})(?::(\d{2}))? ?([AP]M)/i;

/** v0.0.4 🍊 Parses a backend natural-language time into epoch milliseconds (NaN when unrecognized). */
export function parseNaturalTime(text: string | null | undefined): number {
  if (!text) return Number.NaN;
  const m = DATE_TIME.exec(text);
  if (!m) return Number.NaN;
  const [, mon = '', day = '1', year, hh = '0', mm = '0', ss = '0', ampm = 'AM'] = m;
  const month = MONTHS.indexOf(mon.toLowerCase());
  if (month < 0) return Number.NaN;
  let hours = Number(hh) % 12;
  if (ampm.toUpperCase() === 'PM') hours += 12;
  const now = new Date();
  const y = year ? Number(year) : now.getFullYear();
  const date = new Date(y, month, Number(day), hours, Number(mm), Number(ss));
  // A compact time without a year that lands far in the future belongs to last year (Dec → Jan).
  if (!year && date.getTime() - now.getTime() > 2 * 86_400_000) date.setFullYear(y - 1);
  return date.getTime();
}

/** v0.0.4 🍊 "11:32 AM" from any backend time string (falls back to the input). */
export function clockTime(text: string | null | undefined): string {
  if (!text) return '';
  const m = CLOCK.exec(text);
  return m ? `${m[1]}:${m[2]} ${(m[4] ?? '').toUpperCase()}` : text;
}

/** v0.0.4 🍊 "11:32:05 AM" from any backend time string (falls back to the input). */
export function clockTimeWithSeconds(text: string | null | undefined): string {
  if (!text) return '';
  const m = CLOCK.exec(text);
  return m ? `${m[1]}:${m[2]}:${m[3] ?? '00'} ${(m[4] ?? '').toUpperCase()}` : text;
}

/** v0.0.4 🍊 Formats a date in the backend's compact form: "Sat Sep 19, 11:32:05 AM". */
export function formatCompactTime(date: Date): string {
  const h = date.getHours() % 12 || 12;
  return `${WEEKDAYS_SHORT[date.getDay()]} ${MONTHS_SHORT[date.getMonth()]} ${date.getDate()}, ${h}:${pad(
    date.getMinutes(),
  )}:${pad(date.getSeconds())} ${date.getHours() < 12 ? 'AM' : 'PM'}`;
}

/** v0.0.4 🍊 Formats a date in the backend's full form: "Saturday, September 19, 2026 at 11:32:05 AM". */
export function formatFullTime(date: Date): string {
  const h = date.getHours() % 12 || 12;
  return `${WEEKDAYS_LONG[date.getDay()]}, ${MONTHS_LONG[date.getMonth()]} ${date.getDate()}, ${date.getFullYear()} at ${h}:${pad(
    date.getMinutes(),
  )}:${pad(date.getSeconds())} ${date.getHours() < 12 ? 'AM' : 'PM'}`;
}

/** v0.0.4 🍊 Seconds between two backend times, or null when either cannot be parsed. */
export function secondsBetween(from: string, to: string): number | null {
  const a = parseNaturalTime(from);
  const b = parseNaturalTime(to);
  return Number.isNaN(a) || Number.isNaN(b) ? null : Math.round((b - a) / 1000);
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}
