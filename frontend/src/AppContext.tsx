// Everything the start page knows, in one place: the student's profile and language, the data
// loaded from the backend, what they ticked off, and the actions components share (open the chat,
// a journey, the planner…). State that must survive a reload is kept in localStorage, because there
// are no accounts yet (docs/decisions.md #12).
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { initialLang, makeT, type Lang, type T } from './i18n/i18n';
import { getJson, postJson } from './lib/api';
import { KEYS, store } from './lib/store';
import { termFromIndex, termId, termIndex, termOf } from './lib/dates';
import type {
  Catalogue, Department, Feed, HistoryEntry, Journey, JourneyState, ManualSlot, Plan, PlanResult, Profile, ProgrammeInfo,
  Reminder, Semester, Timetable, Upcoming,
} from './lib/types';

export interface ChatRequest { question?: string; voice?: boolean; nonce: number }
export interface PlannerRequest { params?: Record<string, string> | null; nonce: number }

interface App {
  lang: Lang; setLang: (l: Lang) => void; t: T;
  departments: Department[];
  profile: Profile | null; saveProfile: (p: Profile) => void;
  calendar: Upcoming[]; calendarOk: boolean;
  feed: Feed; feedOk: boolean; loadFeed: (department: string | null) => Promise<Feed | null>;
  journeys: Journey[];
  journeyStates: Record<string, JourneyState>; saveJourney: (id: string, patch: JourneyState) => void;
  history: Record<string, HistoryEntry>; recordDone: (key: string, checked: boolean, entry?: Omit<HistoryEntry, 'at'>) => void;
  reminders: Reminder[]; setReminders: (r: Reminder[]) => void;
  doneIds: Set<string>; setDone: (id: string, done: boolean) => void;
  read: Set<string>; markRead: (ids: string[]) => void;
  previousVisit: string | null;
  semester: Semester | null | undefined;
  journeyConfirm: { id: string; text: string } | null; setJourneyConfirm: (c: { id: string; text: string } | null) => void;
  // study planner
  programmes: ProgrammeInfo[]; myProgramme: ProgrammeInfo | null;
  plan: Plan | null; setPlan: (p: Plan | null) => void;
  planResult: PlanResult | null; setPlanResult: (r: PlanResult | null) => void;
  catalogue: Catalogue | null; ensureCatalogue: (id: string, fresh?: boolean) => Promise<Catalogue>;
  timetable: Timetable; loadTimetable: (programme: string) => Promise<void>;
  manualSlots: Record<string, ManualSlot[]>; setManualSlots: (s: Record<string, ManualSlot[]>) => void;
  // opening things
  chatRequest: ChatRequest | null; openChat: (question?: string, voice?: boolean) => void;
  plannerRequest: PlannerRequest | null; openPlanner: (params?: Record<string, string> | null) => void; closePlanner: () => void;
  journeyOpen: string | null; openJourney: (id: string | null) => void;
  profileOpen: boolean; openProfile: (open: boolean) => void;
  feedbackOpen: boolean; openFeedback: (open: boolean) => void;
}

const Ctx = createContext<App | null>(null);
export const useApp = (): App => {
  const app = useContext(Ctx);
  if (!app) throw new Error('useApp outside AppProvider');
  return app;
};

/** Older profiles stored the semester number; turn it into a study start. */
function migrateProfile(p: Profile | null): Profile | null {
  if (p && !p.start && p.semester) {
    p = { ...p, start: termId(termFromIndex(termIndex(termOf(new Date())) - (p.semester - 1))) };
    delete p.semester;
    store.set(KEYS.profile, p);
  }
  return p;
}

/** useState that also writes to localStorage. */
function usePersistent<V>(key: string, initial: V): [V, (v: V) => void] {
  const [value, setValue] = useState<V>(() => store.get(key, initial));
  const set = useCallback((v: V) => { setValue(v); store.set(key, v); }, [key]);
  return [value, set];
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(() => initialLang(store.get(KEYS.lang, null)));
  const t = useMemo(() => makeT(lang), [lang]);
  const setLang = useCallback((l: Lang) => { setLangState(l); store.set(KEYS.lang, l); }, []);
  useEffect(() => { document.documentElement.lang = lang; }, [lang]);

  const [previousVisit] = useState<string | null>(() => {
    const previous = store.get<string | null>(KEYS.lastVisit, null);
    store.set(KEYS.lastVisit, new Date().toISOString());
    return previous;
  });

  const [departments, setDepartments] = useState<Department[]>([]);
  const [profile, setProfile] = useState<Profile | null>(() => migrateProfile(store.get<Profile | null>(KEYS.profile, null)));
  const saveProfile = useCallback((p: Profile) => { setProfile(p); store.set(KEYS.profile, p); }, []);
  const department = (profile && profile.department) || null;

  const [calendar, setCalendar] = useState<Upcoming[]>([]);
  const [calendarOk, setCalendarOk] = useState(true);
  const [feed, setFeed] = useState<Feed>({ news: [], events: [], departmentCovered: false });
  const [feedOk, setFeedOk] = useState(true);
  const [journeys, setJourneys] = useState<Journey[]>([]);
  const [semester, setSemester] = useState<Semester | null | undefined>(undefined);
  const [journeyConfirm, setJourneyConfirm] = useState<{ id: string; text: string } | null>(null);

  const [journeyStates, setJourneyStates] = usePersistent<Record<string, JourneyState>>(KEYS.journeys, {});
  const statesRef = useRef(journeyStates);
  statesRef.current = journeyStates;
  const saveJourney = useCallback((id: string, patch: JourneyState) => {
    setJourneyStates({ ...statesRef.current, [id]: { ...(statesRef.current[id] || {}), ...patch } });
  }, [setJourneyStates]);

  const [history, setHistory] = usePersistent<Record<string, HistoryEntry>>(KEYS.history, {});
  const historyRef = useRef(history);
  historyRef.current = history;
  /** Remembers when something was ticked off (or forgets it when unticked) for the "Done" list. */
  const recordDone = useCallback((key: string, checked: boolean, entry?: Omit<HistoryEntry, 'at'>) => {
    const next = { ...historyRef.current };
    if (checked && entry) next[key] = { at: Date.now(), ...entry };
    else delete next[key];
    setHistory(next);
  }, [setHistory]);

  const [reminders, setReminders] = usePersistent<Reminder[]>(KEYS.reminders, []);
  const [doneList, setDoneList] = usePersistent<string[]>(KEYS.done, []);
  const doneIds = useMemo(() => new Set(doneList), [doneList]);
  const setDone = useCallback((id: string, done: boolean) => {
    const s = new Set(doneList);
    if (done) s.add(id); else s.delete(id);
    setDoneList([...s]);
  }, [doneList, setDoneList]);
  const [readList, setReadList] = usePersistent<string[]>(KEYS.read, []);
  const read = useMemo(() => new Set(readList), [readList]);
  const markRead = useCallback((ids: string[]) => setReadList([...new Set([...readList, ...ids])].slice(-300)), [readList, setReadList]);

  // ---- loading ----
  const loadFeed = useCallback((dept: string | null) => {
    const params = new URLSearchParams({ lang });
    if (dept) params.set('department', dept);
    return getJson<Feed>(`/api/feed?${params}`).catch(() => null);
  }, [lang]);

  useEffect(() => {
    getJson<{ departments: Department[] }>('/api/university').then((u) => setDepartments(u.departments)).catch(() => setDepartments([]));
  }, []);

  // Key dates, journeys, news headlines and the semester come translated from the server.
  useEffect(() => {
    let live = true;
    const params = new URLSearchParams({ lang });
    if (department) params.set('department', department);
    getJson<Upcoming[]>(`/api/calendar?${params}`).then((c) => { if (live) { setCalendar(c); setCalendarOk(true); } }).catch(() => live && setCalendarOk(false));
    loadFeed(department).then((f) => { if (!live) return; if (f) setFeed(f); setFeedOk(Boolean(f)); });
    if (profile && profile.start) {
      const jp = new URLSearchParams({ lang, start: profile.start });
      if (department) jp.set('department', department);
      getJson<Journey[]>(`/api/journeys?${jp}`).then((j) => live && setJourneys(j)).catch(() => live && setJourneys([]));
    } else {
      setJourneys([]);
    }
    setJourneyConfirm(null);
    return () => { live = false; };
  }, [lang, department, profile, loadFeed]);

  useEffect(() => {
    fetch(`/api/semester?lang=${lang}`).then((r) => (r.ok ? r.json() : null)).then(setSemester).catch(() => setSemester(null));
  }, [lang]);

  // ---- study planner ----
  const [programmes, setProgrammes] = useState<ProgrammeInfo[]>([]);
  const [plan, setPlanState] = useState<Plan | null>(() => store.get<Plan | null>(KEYS.plan, null));
  const setPlan = useCallback((p: Plan | null) => { setPlanState(p); store.set(KEYS.plan, p); }, []);
  const [planResult, setPlanResult] = useState<PlanResult | null>(null);
  const [catalogue, setCatalogue] = useState<Catalogue | null>(null);
  const catalogueRef = useRef<Catalogue | null>(null);
  const ensureCatalogue = useCallback(async (id: string, fresh?: boolean) => {
    if (!fresh && catalogueRef.current && catalogueRef.current.programme.id === id) return catalogueRef.current;
    const c = await getJson<Catalogue>(`/api/programmes/${encodeURIComponent(id)}`);
    catalogueRef.current = c;
    setCatalogue(c);
    return c;
  }, []);
  const [timetable, setTimetable] = useState<Timetable>({ available: [], slots: [] });
  const loadTimetable = useCallback(async (programme: string) => {
    try { setTimetable(await getJson<Timetable>(`/api/timetable?programme=${encodeURIComponent(programme)}`)); } catch { setTimetable({ available: [], slots: [] }); }
  }, []);
  const [manualSlots, setManualSlots] = usePersistent<Record<string, ManualSlot[]>>(KEYS.slots, {});

  useEffect(() => { getJson<ProgrammeInfo[]>('/api/programmes').then(setProgrammes).catch(() => setProgrammes([])); }, []);

  /** The programme the student's profile points to, if the planner supports it. */
  const myProgramme = useMemo(() => {
    const name = ((profile && profile.programme) || '').trim().toLowerCase();
    return programmes.find((x) => x.imported && (!profile || !profile.department || x.department === profile.department)
      && x.names.some((n) => n.toLowerCase() === name)) || null;
  }, [programmes, profile]);

  // The saved plan is checked once, so the promo card can show it and the chat knows this semester's modules.
  useEffect(() => {
    if (!myProgramme || !plan || plan.programme !== myProgramme.id || planResult) return;
    ensureCatalogue(myProgramme.id).then(() => postJson<PlanResult>('/api/planner/check', {
      programme: plan.programme, focus: plan.focus, start: plan.start, semesters: plan.semesters, completed: plan.completed, maxCreditsPerSemester: plan.max,
    })).then(setPlanResult).catch(() => setPlanResult(null));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [myProgramme]);

  // ---- opening things ----
  const nonce = useRef(0);
  const [chatRequest, setChatRequest] = useState<ChatRequest | null>(null);
  const openChat = useCallback((question?: string, voice?: boolean) => setChatRequest({ question, voice, nonce: ++nonce.current }), []);
  const [plannerRequest, setPlannerRequest] = useState<PlannerRequest | null>(null);
  const openPlanner = useCallback((params?: Record<string, string> | null) => setPlannerRequest({ params: params || null, nonce: ++nonce.current }), []);
  const closePlanner = useCallback(() => setPlannerRequest(null), []);
  const [journeyOpen, openJourney] = useState<string | null>(null);
  const [profileOpen, openProfile] = useState(false);
  const [feedbackOpen, openFeedback] = useState(false);

  const value: App = {
    lang, setLang, t, departments, profile, saveProfile, calendar, calendarOk, feed, feedOk, loadFeed, journeys,
    journeyStates, saveJourney, history, recordDone, reminders, setReminders, doneIds, setDone, read, markRead,
    previousVisit, semester, journeyConfirm, setJourneyConfirm,
    programmes, myProgramme, plan, setPlan, planResult, setPlanResult, catalogue, ensureCatalogue, timetable, loadTimetable,
    manualSlots, setManualSlots,
    chatRequest, openChat, plannerRequest, openPlanner, closePlanner, journeyOpen, openJourney, profileOpen, openProfile,
    feedbackOpen, openFeedback,
  };
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
