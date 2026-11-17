// Start-page logic that doesn't draw anything: which journey to offer, the to-do list, suggestions,
// the "For you" feed and notifications. Components call these with the current state.
import type { T } from '../i18n/i18n';
import { DAY, WEEKDAY, TIME, capitalize, countdown, daysFromToday, fmt, semesterOf } from './dates';
import type { Feed, FeedItem, HistoryEntry, Journey, JourneyState, Profile, Reminder, Upcoming } from './types';

export function journeyProgress(j: Journey, states: Record<string, JourneyState>) {
  const s = states[j.id] || {};
  const done = new Set(s.done || []);
  return { state: s, done, count: j.steps.filter((step) => done.has(step.id)).length };
}

/** The journey to offer right now, or null: not finished, not snoozed, reminder (if any) due. */
export function offeredJourney(journeys: Journey[], states: Record<string, JourneyState>): Journey | null {
  const now = Date.now();
  for (const j of journeys) {
    const { state: s, count } = journeyProgress(j, states);
    if (count >= j.steps.length) continue;
    if (s.snoozeUntil && s.snoozeUntil > now) continue;
    if (s.remindAt && s.remindAt > now) continue;
    return j;
  }
  return null;
}

export interface TodoItem { id: string; what: string; due: string; soon: boolean; done: boolean; kind: 'cal' | 'rem' | 'step'; ref: string; journey?: string }

/** Actions from key dates, the student's reminders and the next steps of active journeys. */
export function todos(t: T, calendar: Upcoming[], doneIds: Set<string>, reminders: Reminder[], journeys: Journey[], states: Record<string, JourneyState>): TodoItem[] {
  const items: TodoItem[] = calendar
    .filter((e) => e.action && ((e.status === 'UPCOMING' && e.daysLeft <= 30) || (e.status === 'ONGOING' && -daysFromToday(e.start) <= 14)))
    .map((e) => {
      const name = e.title.split(',')[0];
      const due = e.status === 'UPCOMING' ? `${name} ${countdown(t, e)}` : t('todo.started', name);
      return { id: `cal:${e.id}`, what: e.action as string, due, soon: e.status === 'ONGOING' || e.daysLeft <= 7, done: doneIds.has(e.id), kind: 'cal' as const, ref: e.id };
    });
  for (const r of reminders.filter((x) => !x.done)) {
    const at = new Date(r.at);
    items.push({ id: `rem:${r.id}`, what: r.title, due: `⏰ ${fmt(t, at, WEEKDAY)}, ${fmt(t, at, TIME)}`, soon: at.getTime() - Date.now() < 2 * DAY, done: false, kind: 'rem', ref: r.id });
  }
  for (const j of journeys) {
    const { done } = journeyProgress(j, states);
    for (const step of j.steps.filter((s) => !done.has(s.id)).slice(0, 2)) {
      items.push({ id: `j:${j.id}:${step.id}`, what: step.title, due: j.title, soon: false, done: false, kind: 'step', ref: step.id, journey: j.id });
    }
  }
  return items;
}

export interface DoneItem extends HistoryEntry { key: string }

/** Ticked-off items, newest first. Items ticked before the history existed show without a date. */
export function historyItems(history: Record<string, HistoryEntry>, journeys: Journey[], states: Record<string, JourneyState>, calendar: Upcoming[], doneIds: Set<string>): DoneItem[] {
  const entries: DoneItem[] = Object.entries(history).map(([key, h]) => ({ key, ...h }));
  const known = new Set(entries.map((h) => h.key));
  for (const j of journeys) {
    for (const step of j.steps) {
      const key = `j:${j.id}:${step.id}`;
      if (journeyProgress(j, states).done.has(step.id) && !known.has(key)) entries.push({ key, at: 0, what: step.title, sub: j.title, journey: j.id });
    }
  }
  for (const e of calendar) {
    const key = `cal:${e.id}`;
    if (e.action && doneIds.has(e.id) && !known.has(key)) entries.push({ key, at: 0, what: e.action, sub: e.title.split(',')[0] });
  }
  return entries.sort((a, b) => b.at - a.at);
}

export interface Chip { text: string; tag?: string; soon: boolean }

/** Up to six suggested questions: the next journey step, then key dates, then general starters. */
export function chips(t: T, journeys: Journey[], states: Record<string, JourneyState>, calendar: Upcoming[]): Chip[] {
  const out: Chip[] = [];
  const seen = new Set<string>();
  const add = (text: string, tag?: string, soon = false) => {
    if (seen.has(text) || seen.size >= 6) return;
    seen.add(text);
    out.push({ text, tag, soon });
  };
  for (const j of journeys) {
    const { done } = journeyProgress(j, states);
    const next = j.steps.find((s) => !done.has(s.id) && s.ask);
    if (next && next.ask) add(next.ask, `🧭 ${j.title}`);
  }
  // Suggestions of a key date only make sense before it and in its first two weeks.
  for (const e of calendar.slice(0, 3)) {
    const fresh = e.status === 'UPCOMING' || -daysFromToday(e.start) <= 14;
    if (!fresh) continue;
    const tag = e.status === 'UPCOMING' ? `⏰ ${countdown(t, e)}` : `⏰ ${t('chip.started')}`;
    for (const s of e.suggestions || []) add(s, tag, e.daysLeft > 7);
  }
  for (const s of t('starters') as { text: string; topic: string }[]) {
    // Skip a general question when a more specific suggestion already covers its topic.
    if (![...seen].some((x) => x.includes(s.topic))) add(s.text);
  }
  return out;
}

// ---- feed ----
const NEWCOMER = /(newcomer|erstsemester|ersti|einführung|introduct|orientation|orientierung|welcome|willkommen|studienstart|o-woche|freshm)/i;

export function shortProgramme(p: string | null | undefined): string { return p ? p.replace(/ ?\([^)]*\)/, '').trim() : ''; }

export function isNew(item: FeedItem, previousVisit: string | null): boolean { return Boolean(previousVisit) && item.firstSeenAt > (previousVisit as string); }

/** Why an item suits a profile: it names their programme, or it is for new students and they are new. */
export function reasonsFor(item: FeedItem, profile: Profile | null): string[] {
  const why: string[] = [];
  const programme = shortProgramme(profile && profile.programme).toLowerCase();
  if (programme.length >= 4 && item.title.toLowerCase().includes(programme)) why.push('programme');
  const sem = semesterOf(profile);
  if (sem !== null && sem <= 2 && NEWCOMER.test(item.title)) why.push('new');
  return why;
}

/** Items for the "For you" column: the department's own, best matches first; otherwise university items that fit. */
export function mineItems(items: FeedItem[], covered: boolean, profile: Profile | null) {
  const dept = profile && profile.department;
  const pool = covered ? items.filter((i) => i.department === dept) : items.filter((i) => !i.department);
  const ranked = pool.map((item, index) => ({ item, why: reasonsFor(item, profile), index }));
  const shown = covered ? ranked : ranked.filter((r) => r.why.length);
  // Stable: more reasons first, otherwise keep the date order the server sent.
  return shown.sort((a, b) => (b.why.length - a.why.length) || (a.index - b.index));
}

// ---- notifications ----
export interface Notification { id: string; icon: string; text: string; sub: string; url?: string; journey?: string }

export function notifications(t: T, args: {
  journeys: Journey[]; states: Record<string, JourneyState>; reminders: Reminder[]; todoItems: TodoItem[]; feed: Feed; previousVisit: string | null;
}): Notification[] {
  const list: Notification[] = [];
  const offered = offeredJourney(args.journeys, args.states);
  if (offered) {
    const { state: s, count } = journeyProgress(offered, args.states);
    list.push({ id: `journey:${offered.id}:${s.remindAt || 'new'}`, icon: '🧭', text: offered.title, sub: t('j.progress', count, offered.steps.length), journey: offered.id });
  }
  for (const r of args.reminders.filter((x) => !x.done && new Date(x.at) <= new Date())) {
    list.push({ id: `rem:${r.id}:due`, icon: '⏰', text: r.title, sub: `${t('rem.due')} · ${fmt(t, new Date(r.at), WEEKDAY)}, ${fmt(t, new Date(r.at), TIME)}` });
  }
  for (const item of args.todoItems) {
    if (!item.done && item.soon && item.kind !== 'rem') list.push({ id: `todo:${item.id}`, icon: '⏰', text: item.what, sub: item.due });
  }
  // Events today or tomorrow, own department first; at most three so the bell stays meaningful.
  const soon = args.feed.events
    .filter((e) => { const n = daysFromToday(e.date); return n >= 0 && n <= 1; })
    .sort((a, b) => (b.department ? 1 : 0) - (a.department ? 1 : 0))
    .slice(0, 3);
  for (const e of soon) list.push({ id: `event:${e.url}`, icon: '📅', text: e.title, sub: `${capitalize(t('rel', daysFromToday(e.date)))} · ${e.source}`, url: e.url });
  for (const i of args.feed.news.filter((n) => isNew(n, args.previousVisit)).slice(0, 3)) {
    list.push({ id: `news:${i.url}`, icon: '🆕', text: i.title, sub: t('notif.newFrom', i.source), url: i.url });
  }
  return list;
}

/** Downloads a one-event calendar file so a reminder also lives in the student's own calendar. */
export function downloadIcs(t: T, item: { id: string; title: string }, when: Date): void {
  const stamp = (d: Date) => d.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
  const end = new Date(when.getTime() + 15 * 60000);
  const text = (s: string) => s.replace(/[\\,;]/g, (c) => `\\${c}`).replace(/\n/g, '\\n');
  const ics = [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Study Assistant//EN', 'BEGIN:VEVENT',
    `UID:${item.id}-${when.getTime()}@study-assistant`, `DTSTAMP:${stamp(new Date())}`,
    `DTSTART:${stamp(when)}`, `DTEND:${stamp(end)}`,
    `SUMMARY:${text(item.title)}`, `DESCRIPTION:${text(`${t('j.icsDesc')} ${location.origin}/`)}`,
    'BEGIN:VALARM', 'TRIGGER:PT0M', 'ACTION:DISPLAY', `DESCRIPTION:${text(item.title)}`, 'END:VALARM',
    'END:VEVENT', 'END:VCALENDAR',
  ].join('\r\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([ics], { type: 'text/calendar' }));
  a.download = `${item.id}.ics`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}
