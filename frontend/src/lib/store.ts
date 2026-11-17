// Browser storage can be unavailable (private mode, blocked site data); the page works without it.
export const KEYS = {
  profile: 'uka.profile', done: 'uka.done', read: 'uka.read', lastVisit: 'uka.lastVisit', lang: 'uka.lang',
  journeys: 'uka.journeys', history: 'uka.history', reminders: 'uka.reminders', plan: 'uka.plan', planView: 'uka.planView',
  slots: 'uka.slots',
} as const;

export const store = {
  get<T>(key: string, fallback: T): T {
    try { const v = localStorage.getItem(key); return v === null ? fallback : (JSON.parse(v) as T); } catch { return fallback; }
  },
  set(key: string, value: unknown): void {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* ignore */ }
  },
};
