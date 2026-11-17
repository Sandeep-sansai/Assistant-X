// Names from the university's pages (modules, areas, focus options) in the chosen language. The
// pages are German; English versions come from the server once they are translated.
import type { T } from '../i18n/i18n';
import type { Catalogue, Plan, PlanResult, ProgrammeInfo } from '../lib/types';

interface NameContext { lang: 'en' | 'de'; t: T; catalogue: Catalogue | null; programmes: ProgrammeInfo[]; plan: Plan | null; myProgramme: ProgrammeInfo | null }

export function uiTitle(c: Pick<NameContext, 'lang' | 'catalogue'>, text: string): string {
  if (!text || c.lang !== 'en') return text;
  const english = (c.catalogue && c.catalogue.english) || {};
  return english[text] || text;
}

const FOCUS_ICONS: Record<string, string> = { CIDA: '🧠', EInf: '⚡', IDG: '🌐', SW: '🛠', UW: '🌱' };

/** "Schwerpunkt Softwareentwicklung (SW)" → { abbr: 'SW', name: 'Softwareentwicklung', icon }. */
export function focusShort(c: NameContext, code: string): { abbr: string | null; name: string; icon: string } {
  const prog = c.programmes.find((x) => x.id === (c.plan && c.plan.programme)) || c.myProgramme;
  const f = prog && prog.focusOptions.find((x) => x.code === code);
  let title = code;
  if (f) title = c.lang === 'en' ? (f.titleEn || uiTitle(c, f.title)) : f.title;
  title = title.replace(/^Schwerpunkt\s+/, '');
  const m = /\(([^)]+)\)\s*$/.exec(title);
  const abbr = m ? m[1] : null;
  const name = title.replace(/\s*\([^)]*\)\s*$/, '');
  return { abbr, name: abbr ? name : (/frei|free/i.test(title) ? c.t('plan.free') : name), icon: abbr ? (FOCUS_ICONS[abbr] || '◆') : '✨' };
}

/** Short display name of a requirement: its English name, or the German one without "Wahlpflicht". */
export function shortRequirement(c: Pick<NameContext, 'lang' | 'catalogue' | 't'>, title: string): string {
  const english = uiTitle(c, title);
  if (english !== title) return english;
  const name = title.replace(/^Wahl(?:pflicht|plficht)\s+/, '');
  return / oder /.test(name) && name.includes(',') ? c.t('plan.freeElective') : name;
}

export function requirementTitle(c: Pick<NameContext, 'lang' | 'catalogue' | 't'>, result: PlanResult | null, code: string): string {
  const r = result && result.requirements.find((x) => x.code === code);
  return r ? shortRequirement(c, r.title) : '';
}

// eCampus only has German labels; with English chosen they are shown in English.
const KIND_EN: [RegExp, string][] = [[/Vorlesung\/Übung/g, 'Lecture/exercise'], [/Vorlesung/g, 'Lecture'], [/Übungen/g, 'Exercises'], [/Übung/g, 'Exercise'],
  [/Tutorium/g, 'Tutorial'], [/Praktikum/g, 'Practical'], [/Labor/g, 'Lab'], [/Projekt/g, 'Project'], [/Kolloquium/g, 'Colloquium'],
  [/Blockveranstaltung/g, 'Block course'], [/(\d+)\. PG/g, 'group $1']];
const ROOM_EN: [RegExp, string][] = [[/Hörsaal/g, 'Lecture hall'], [/Seminarraum/g, 'Seminar room'], [/Raum/g, 'Room'], [/Labor/g, 'Lab'],
  [/-altes Gebäude/g, ' old building'], [/-neues Gebäude/g, ' new building'], [/Gebäude/g, 'building'], [/\(EG\)/g, '(ground floor)'],
  [/\((\d)\. ?OG\)/g, '(floor $1)'], [/\((\d)\. ?UG\)/g, '(basement $1)'], [/\bUG\b/g, 'basement'], [/\bOG\b/g, 'floor']];
const english = (lang: string, text: string | null | undefined, rules: [RegExp, string][]) =>
  (lang === 'en' && text) ? rules.reduce((x, [re, to]) => x.replace(re, to), text) : (text || '');
export const kindLabel = (lang: string, kind: string | null | undefined) => english(lang, kind, KIND_EN);
export const roomLabel = (lang: string, room: string | null | undefined) => english(lang, room, ROOM_EN);

export const OFFERED_BADGE: Record<string, string> = { WINTER: 'plan.winter', SUMMER: 'plan.summer', BOTH: 'plan.both', UNKNOWN: 'plan.unknown' };
export const TERM_LETTER: Record<string, string> = { WINTER: 'W', SUMMER: 'S', BOTH: 'W·S', UNKNOWN: '?' };
