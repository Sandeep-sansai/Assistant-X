import { STR, type Lang } from './strings';

export type { Lang };
export type T = (key: string, ...args: any[]) => any;

/** A text lookup for one language; function entries are called with the arguments. */
export function makeT(lang: Lang): T {
  return (key, ...args) => {
    const v = STR[lang][key] ?? STR.en[key];
    return typeof v === 'function' ? v(...args) : v;
  };
}

export function initialLang(saved: unknown): Lang {
  if (saved === 'en' || saved === 'de') return saved;
  return (navigator.language || '').toLowerCase().startsWith('de') ? 'de' : 'en';
}
