// Shapes of the data the backend sends (see the web/ controllers).
import type { Upcoming } from './dates';
export type { Upcoming };

export interface Profile { department: string | null; programme: string | null; start: string | null; semester?: number }
export interface Department { id: string; name: string; programmes: string[]; hasFeed: boolean }

export interface FeedItem { title: string; url: string; date: string; category?: string | null; source: string; department?: string | null; firstSeenAt: string }
export interface Feed { news: FeedItem[]; events: FeedItem[]; departmentCovered: boolean }

export interface JourneyStep { id: string; title: string; detail: string; ask?: string | null; link?: string | null; actionLink?: string | null; actionLabel?: string | null; verifiedOn?: string | null }
export interface Journey { id: string; title: string; prompt: string; steps: JourneyStep[] }
export interface JourneyState { done?: string[]; snoozeUntil?: number | null; remindAt?: number | null }

export interface Reminder { id: string; title: string; at: string; done: boolean }
export interface HistoryEntry { at: number; what: string; sub?: string; journey?: string }

export interface Semester { title: string; lectureStart: string; lectureEnd: string; breaks: { title: string; start: string; end: string }[]; sourceUrl?: string }

export interface ReplyCard { title: string; detail?: string | null; url?: string | null; date?: string | null }
export interface ReplyAction { type: string; label: string; title?: string; at?: string; journeyId?: string; params?: Record<string, string> }
export interface ReplySource { number: number; title: string; url: string; retrievedAt: string }
export interface Reply { agent: string; text: string; cards?: ReplyCard[]; actions?: ReplyAction[]; sources?: ReplySource[]; questionId?: number | null }

export interface FocusOption { code: string; title: string; titleEn?: string | null; credits: number }
export interface ProgrammeInfo {
  id: string; title: string; department: string; names: string[]; imported: boolean; regularSemesters: number | null;
  totalCredits: number | null; focusOptions: FocusOption[]; sourceUrl: string; fetchedAt: string | null;
}
export interface Module { code: string; title: string; credits: number; offered: 'WINTER' | 'SUMMER' | 'BOTH' | 'UNKNOWN'; oralExam: boolean; url: string }
export interface Group { code: string; title: string; credits: number | null; modules: string[] }
export interface Catalogue {
  programme: { id: string; title: string; regularSemesters: number; thesisCredits: number; groups: Group[]; modules: Record<string, Module> };
  english: Record<string, string>;
}

export interface Planned { code: string; title: string; credits: number; offered: Module['offered']; oralExam: boolean; url: string; requirement?: string | null; thesis: boolean }
export interface PlanSemester { number: number; term: string; winter: boolean; credits: number; modules: Planned[] }
export interface Requirement { code: string; title: string; required: number; planned: number; met: boolean }
export interface PlanWarning { type: string; params: any[] }
export interface PlanResult { credits: number; requiredCredits: number; semesters: PlanSemester[]; requirements: Requirement[]; warnings: PlanWarning[]; notes: PlanWarning[] }
export interface Plan { programme: string; focus: string; start: string; max: number | null; completed: string[]; manual?: boolean; semesters: string[][] }

export interface StoredSlot { term: string; courseTitle: string; moduleCode: string | null; kind: string | null; weekday: number; start: string; end: string; room: string | null; sourceUrl: string | null }
export interface Timetable { available: string[]; slots: StoredSlot[] }
export interface ManualSlot { weekday: number; start: string; end: string; kind: string }
