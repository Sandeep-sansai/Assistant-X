import type { T } from '../i18n/i18n';

export const DAY = 86400000;
export function parseDay(iso: string): Date { const [y, m, d] = iso.split('-').map(Number); return new Date(y, m - 1, d); }
export function today(): Date { const n = new Date(); return new Date(n.getFullYear(), n.getMonth(), n.getDate()); }
export function daysFromToday(iso: string): number { return Math.round((parseDay(iso).getTime() - today().getTime()) / DAY); }
export function fmt(t: T, date: Date, opts: Intl.DateTimeFormatOptions): string { return new Intl.DateTimeFormat(t('locale'), opts).format(date); }
export const SHORT: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short' };
export const LONG: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short', year: 'numeric' };
export const WEEKDAY: Intl.DateTimeFormatOptions = { weekday: 'short', day: 'numeric', month: 'short' };
export const TIME: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' };
export function capitalize(s: string): string { return s.charAt(0).toUpperCase() + s.slice(1); }

export interface Upcoming {
  id: string; title: string; start: string; end?: string | null; status: 'UPCOMING' | 'ONGOING'; daysLeft: number;
  action?: string | null; sourceUrl?: string | null; verifiedOn?: string | null; suggestions?: string[];
}

/** Short countdown phrase for a calendar entry, e.g. "starts tomorrow", "ends in 9 days". */
export function countdown(t: T, entry: Upcoming): string {
  const n = entry.daysLeft;
  if (!entry.end) return t('rel', n);
  if (entry.status === 'UPCOMING') return t('starts', t('rel', n));
  return n === 0 ? t('endsToday') : t('ends', t('rel', n));
}

// ---- semesters ----
// Winter semester y runs 1 Oct y – 31 Mar y+1; summer semester y runs 1 Apr – 30 Sep y.
export interface Term { ws: boolean; year: number }
export function termOf(date: Date): Term {
  const m = date.getMonth() + 1;
  const y = date.getFullYear();
  if (m >= 10) return { ws: true, year: y };
  if (m <= 3) return { ws: true, year: y - 1 };
  return { ws: false, year: y };
}
export function termIndex(term: Term): number { return term.year * 2 + (term.ws ? 1 : 0); }
export function termFromIndex(i: number): Term { return { ws: i % 2 === 1, year: Math.floor(i / 2) }; }
export function termId(term: Term): string { return (term.ws ? 'WS' : 'SS') + term.year; }
export function parseTermId(id: string | null | undefined): Term | null {
  const m = /^(WS|SS)(\d{4})$/.exec(id || '');
  return m ? { ws: m[1] === 'WS', year: Number(m[2]) } : null;
}
export function termLabel(t: T, term: Term): string { return term.ws ? t('term.ws', term.year) : t('term.ss', term.year); }
export function termStartDate(term: Term): Date { return term.ws ? new Date(term.year, 9, 1) : new Date(term.year, 3, 1); }

/** Current semester number for a study start such as "WS2025", or null if unknown. */
export function semesterOf(profile: { start?: string | null } | null | undefined): number | null {
  const start = parseTermId(profile && profile.start);
  if (!start) return null;
  return termIndex(termOf(new Date())) - termIndex(start) + 1;
}

/** "today", "yesterday", "3 days ago" for a timestamp. */
export function relDone(t: T, at: number): string {
  const d = new Date(at);
  return t('rel', Math.round((new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime() - today().getTime()) / DAY));
}
