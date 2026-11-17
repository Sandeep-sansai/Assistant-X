// Week view and clashes. Imported times come from the eCampus course catalogue (GET /api/timetable);
// times a student adds by hand are kept in the browser per module and apply to every semester the
// module is planned in.
import type { Catalogue, ManualSlot, PlanResult, PlanSemester, Timetable } from '../lib/types';

export const DAYS = ['', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'];
export const WK_FROM = 8;
export const WK_TO = 20;
export const WK_COLORS = ['#e1e8f5', '#dee489', '#f8d3e2', '#d5ecd9', '#fde3c3', '#e2dcf3', '#cfe9ef', '#f3e1d6'];

export const minutes = (hhmm: string) => { const [h, m] = String(hhmm).split(':').map(Number); return h * 60 + (m || 0); };
export const hhmm = (min: number) => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`;

export interface Slot { code: string; kind: string | null; weekday: number; start: number; end: number; room?: string | null; url?: string | null; manual?: number; group?: boolean }

/**
 * The semester whose imported times stand for {@code term}: the term itself, or else the latest
 * imported semester of the same season (times usually repeat yearly), marked as an estimate.
 */
export function timetableTerm(timetable: Timetable, term: string): { term: string; estimate: boolean } | null {
  const available = timetable.available || [];
  if (available.includes(term)) return { term, estimate: false };
  const same = available.find((x) => x.slice(0, 2) === term.slice(0, 2));
  return same ? { term: same, estimate: true } : null;
}

/** Every slot of the given modules in one semester: imported ones and the student's own. */
export function semesterSlots(timetable: Timetable, manual: Record<string, ManualSlot[]>, semester: { term: string; modules: { code: string }[] }) {
  const codes = new Set(semester.modules.map((m) => m.code));
  const source = timetableTerm(timetable, semester.term);
  const slots: Slot[] = [];
  if (source) {
    for (const x of timetable.slots) {
      if (x.term === source.term && x.moduleCode && codes.has(x.moduleCode)) {
        slots.push({ code: x.moduleCode, kind: x.kind, weekday: x.weekday, start: minutes(x.start), end: minutes(x.end), room: x.room, url: x.sourceUrl });
      }
    }
  }
  for (const code of codes) {
    (manual[code] || []).forEach((x, i) => slots.push({ code, kind: x.kind, weekday: x.weekday, start: minutes(x.start), end: minutes(x.end), manual: i }));
  }
  // Alternative groups: eCampus marks them "· 2. PG"; for hand-entered times, several slots of the
  // same type of one module count as groups. Attending one of them is enough.
  const counts: Record<string, number> = {};
  for (const x of slots) if (x.manual !== undefined) counts[`${x.code}|${x.kind || ''}`] = (counts[`${x.code}|${x.kind || ''}`] || 0) + 1;
  for (const x of slots) x.group = /\d+\. PG/.test(x.kind || '') || (x.manual !== undefined && counts[`${x.code}|${x.kind || ''}`] > 1);
  return { slots, source };
}

export interface Clash { a: Slot; b: Slot; soft: boolean }

/** Pairs of overlapping slots of different modules; soft when one side is just one of several groups. */
export function clashes(slots: Slot[]): Clash[] {
  const found: Clash[] = [];
  for (let i = 0; i < slots.length; i++) {
    for (let j = i + 1; j < slots.length; j++) {
      const a = slots[i]; const b = slots[j];
      if (a.code !== b.code && a.weekday === b.weekday && a.start < b.end && b.start < a.end) found.push({ a, b, soft: Boolean(a.group || b.group) });
    }
  }
  return found;
}

/** Side-by-side columns for overlapping slots of one day: each slot gets a lane and the lane count of its cluster. */
export function layoutLanes(daySlots: Slot[]): Map<Slot, { lane: number; of: number }> {
  const result = new Map<Slot, { lane: number; of: number }>();
  let cluster: Slot[] = [];
  let clusterEnd = -1;
  const close = () => {
    const ends: number[] = [];
    for (const x of cluster) {
      let lane = ends.findIndex((end) => end <= x.start);
      if (lane < 0) { lane = ends.length; ends.push(0); }
      ends[lane] = x.end;
      result.set(x, { lane, of: 0 });
    }
    for (const x of cluster) (result.get(x) as { of: number }).of = ends.length;
    cluster = [];
  };
  for (const x of daySlots) {
    if (cluster.length && x.start >= clusterEnd) close();
    cluster.push(x);
    clusterEnd = Math.max(clusterEnd, x.end);
  }
  if (cluster.length) close();
  return result;
}

/**
 * The nearest other semester (index) where a module is taught and would not clash with what is
 * planned there, or null. Semesters without known times count as free of clashes.
 */
export function freeSemesterFor(result: PlanResult, catalogue: Catalogue, timetable: Timetable, manual: Record<string, ManualSlot[]>, code: string, from: number): number | null {
  const m = catalogue.programme.modules[code];
  if (!m) return null;
  const order = result.semesters.map((_, i) => i).filter((i) => i !== from && !result.semesters[i].modules.some((x) => x.thesis))
    .sort((a, b) => Math.abs(a - from) - Math.abs(b - from));
  for (const i of order) {
    const sem = result.semesters[i];
    const taught = m.offered === 'BOTH' || m.offered === 'UNKNOWN' || (m.offered === 'WINTER') === sem.winter;
    if (!taught) continue;
    const trial = { term: sem.term, modules: [...sem.modules, { code }] };
    if (!clashes(semesterSlots(timetable, manual, trial).slots).some((c) => !c.soft && (c.a.code === code || c.b.code === code))) return i;
  }
  return null;
}

/** Number of hard clashes in each semester, for the tabs and the "Check" card. */
export function hardClashes(timetable: Timetable, manual: Record<string, ManualSlot[]>, s: PlanSemester): number {
  return clashes(semesterSlots(timetable, manual, s).slots).filter((c) => !c.soft).length;
}
