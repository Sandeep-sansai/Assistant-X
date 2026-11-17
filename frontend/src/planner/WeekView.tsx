import { useState } from 'react';
import { useApp } from '../AppContext';
import { parseTermId, termLabel } from '../lib/dates';
import { kindLabel, roomLabel, uiTitle } from './names';
import { usePlanner } from './PlannerContext';
import { SemesterTabs } from './StepView';
import { DAYS, WK_COLORS, WK_FROM, WK_TO, clashes, freeSemesterFor, hardClashes, hhmm, layoutLanes, minutes, semesterSlots, type Slot } from './timetable';

const HOUR = 44;

/** Hand-entered times: saved in this browser per module. */
function AddTimesForm({ modules }: { modules: { code: string; title: string }[] }) {
  const app = useApp();
  const { t, manualSlots, setManualSlots } = app;
  const [code, setCode] = useState('');
  const [day, setDay] = useState('1');
  const [kind, setKind] = useState('');
  const [from, setFrom] = useState('10:00');
  const [to, setTo] = useState('12:00');
  const chosen = modules.some((m) => m.code === code) ? code : (modules[0] && modules[0].code) || '';
  const kinds = ['lecture', 'exercise', 'lab', 'seminar'].map((k) => t(`wk.kind.${k}`) as string);
  return (
    <form className="wk-form" onSubmit={(e) => {
      e.preventDefault();
      if (!chosen || minutes(to) <= minutes(from)) return;
      setManualSlots({ ...manualSlots, [chosen]: [...(manualSlots[chosen] || []), { weekday: Number(day), start: from, end: to, kind: kind || kinds[0] }] });
    }}>
      <label className="wide">{t('wk.module')}<select value={chosen} onChange={(e) => setCode(e.target.value)}>{modules.map((m) => <option key={m.code} value={m.code}>{uiTitle(app, m.title)}</option>)}</select></label>
      <label>{t('wk.day')}<select value={day} onChange={(e) => setDay(e.target.value)}>{[1, 2, 3, 4, 5, 6].map((d) => <option key={d} value={d}>{t(`day.${DAYS[d]}`)}</option>)}</select></label>
      <label>{t('wk.kind')}<select value={kind || kinds[0]} onChange={(e) => setKind(e.target.value)}>{kinds.map((k) => <option key={k} value={k}>{k}</option>)}</select></label>
      <label>{t('wk.from')}<input type="time" step={900} value={from} onChange={(e) => setFrom(e.target.value)} /></label>
      <label>{t('wk.to')}<input type="time" step={900} value={to} onChange={(e) => setTo(e.target.value)} /></label>
      <button className="btn primary wide" type="submit">{t('wk.add')}</button>
    </form>
  );
}

/** One semester's planned modules on a Monday–Friday grid, clashes outlined, with fixes right there. */
export function WeekView() {
  const app = useApp();
  const { t, lang, planResult, catalogue, timetable, manualSlots, setManualSlots } = app;
  const p = usePlanner();
  if (!planResult || !catalogue) return null;
  const index = Math.max(0, Math.min(p.step, planResult.semesters.length - 1));
  const s = planResult.semesters[index];
  const term = parseTermId(s.term);
  const { slots, source } = semesterSlots(timetable, manualSlots, s);
  const slotText = (x: Slot) => `${t(`day.${DAYS[x.weekday]}`)} ${hhmm(x.start)}–${hhmm(x.end)}`;
  let srcText = t('wk.noTimes');
  if (source && !source.estimate) srcText = t('wk.fromCatalogue');
  else if (source) { const st = parseTermId(source.term); srcText = t('wk.estimate', st ? termLabel(t, st) : source.term); }

  // Colour per module, stable within the semester.
  const colour: Record<string, string> = {};
  s.modules.forEach((m, i) => { colour[m.code] = WK_COLORS[i % WK_COLORS.length]; });
  const found = clashes(slots);
  const inClash = new Map<Slot, boolean>();
  for (const c of found) for (const x of [c.a, c.b]) inClash.set(x, (inClash.get(x) || false) || !c.soft);
  const days = slots.some((x) => x.weekday === 6) ? 6 : 5;
  const height = (WK_TO - WK_FROM) * HOUR;
  const timed = new Set(slots.map((x) => x.code));
  const missing = s.modules.filter((m) => !timed.has(m.code) && !m.thesis);
  const formModules = [...missing, ...s.modules.filter((m) => timed.has(m.code) && !m.thesis)];
  const removeManual = (x: Slot) => {
    const list = [...(manualSlots[x.code] || [])];
    list.splice(x.manual as number, 1);
    const next = { ...manualSlots, [x.code]: list };
    if (!list.length) delete next[x.code];
    setManualSlots(next);
  };

  return (
    <div className="pl-week" id="plWeek">
      <SemesterTabs label={(i) => { const n = hardClashes(timetable, manualSlots, planResult.semesters[i]); return `${t('plan.semester', planResult.semesters[i].number)}${n ? ` · ⚠ ${n}` : ''}`; }} />
      <div className="wk-head">
        <h3>{term ? termLabel(t, term) : ''}</h3>
        <span className={`wk-src${source && source.estimate ? ' estimate' : ''}`}>{srcText}</span>
      </div>
      <div className="wk-mods">
        {s.modules.map((m, i) => {
          const times = slots.filter((x) => x.code === m.code);
          return (
            <div className="wk-mod" key={m.code}>
              <i style={{ background: m.thesis ? 'var(--lime)' : WK_COLORS[i % WK_COLORS.length] }} />
              <div><b>{uiTitle(app, m.title)}</b><span>{m.thesis ? t('wk.thesis') : times.length ? times.map((x) => `${slotText(x)}${x.kind ? ` ${kindLabel(lang, x.kind)}` : ''}`).join(' · ') : t('wk.noTimesFor')}</span></div>
            </div>
          );
        })}
      </div>
      <div className="wk-grid" style={{ ['--days' as string]: String(days), ['--hour' as string]: `${HOUR}px` }}>
        <div className="wk-day" />
        {Array.from({ length: days }, (_, d) => <div className="wk-day" key={d}>{t(`day.${DAYS[d + 1]}`)}</div>)}
        <div className="wk-times" style={{ height }}>
          {Array.from({ length: WK_TO - WK_FROM - 1 }, (_, k) => <span key={k} style={{ top: (k + 1) * HOUR }}>{`${WK_FROM + k + 1}:00`}</span>)}
        </div>
        {Array.from({ length: days }, (_, k) => {
          const d = k + 1;
          const daySlots = slots.filter((x) => x.weekday === d).sort((a, b) => a.start - b.start);
          const lanes = layoutLanes(daySlots);
          return (
            <div className="wk-col" key={d} style={{ height }}>
              {daySlots.map((x, n) => {
                const top = Math.max(0, (x.start - WK_FROM * 60) / 60 * HOUR);
                const h = Math.max(18, (Math.min(x.end, WK_TO * 60) - Math.max(x.start, WK_FROM * 60)) / 60 * HOUR - 2);
                const { lane, of } = lanes.get(x) as { lane: number; of: number };
                return (
                  <div key={n} className={`wk-slot${inClash.get(x) ? ' clash' : ''}${x.group ? ' group' : ''}`}
                    title={`${p.name(x.code)} · ${kindLabel(lang, x.kind)} · ${slotText(x)}${x.room ? ` · ${roomLabel(lang, x.room)}` : ''}`}
                    style={{ left: `calc(${lane / of * 100}% + 3px)`, width: `calc(${100 / of}% - 6px)`, right: 'auto', top, height: h, background: colour[x.code] || WK_COLORS[0] }}>
                    <b>{p.name(x.code)}</b>{`${hhmm(x.start)}–${hhmm(x.end)}${x.kind ? ` · ${kindLabel(lang, x.kind)}` : ''}`}
                    {x.room && <div>{roomLabel(lang, x.room)}</div>}
                    {x.manual !== undefined && <button type="button" className="x" aria-label={t('wk.remove')} onClick={() => removeManual(x)}>✕</button>}
                  </div>
                );
              })}
            </div>
          );
        })}
        {!slots.length && <div className="wk-empty">{s.modules.some((m) => !m.thesis) ? t('wk.gridEmpty') : t('plan.emptySem')}</div>}
      </div>
      <div className="wk-legend">
        <span><i style={{ boxShadow: 'inset 0 0 0 2px var(--magenta)' }} />{t('wk.legendClash')}</span>
        <span><i style={{ borderLeft: '3px dashed rgba(0,0,0,.35)', background: 'var(--surface-2)' }} />{t('wk.legendGroup')}</span>
      </div>
      <div className="wk-panels">
        <section className="pl-pane">
          <h4>{t('wk.clashes')}</h4>
          <ul className="wk-clashes">
            {!found.length && <li className="ok">{slots.length ? `✓ ${t('wk.noClashes')}` : t('wk.nothingToCheck')}</li>}
            {found.map((c, k) => (
              <li key={k} className={c.soft ? 'soft' : ''}>
                <span>{t(c.soft ? 'wk.softClash' : 'wk.clash', p.name(c.a.code), slotText(c.a), p.name(c.b.code), slotText(c.b))}</span>
                {/* Fixes right here: move one of the two to a semester where it's taught and doesn't clash, or swap it. */}
                {!c.soft && (
                  <div className="wk-fixes">
                    {[c.a.code, c.b.code].map((code) => {
                      const planned = s.modules.find((m) => m.code === code);
                      const target = freeSemesterFor(planResult, catalogue, timetable, manualSlots, code, index);
                      return (
                        <span key={code} style={{ display: 'contents' }}>
                          {target !== null && <button className="btn" type="button" onClick={() => p.moveModule(code, target)}>{t('wk.moveTo', p.name(code), planResult.semesters[target].number)}</button>}
                          {planned && planned.requirement && !planned.thesis && (
                            <button className="btn" type="button" onClick={() => p.openPicker(index, { code, requirement: planned.requirement as string })}>{t('wk.swap', p.name(code))}</button>
                          )}
                        </span>
                      );
                    })}
                  </div>
                )}
              </li>
            ))}
          </ul>
        </section>
        <section className="pl-pane">
          <h4>{t('wk.addTimes')}</h4>
          {missing.length > 0 && <div className="wk-missing"><span>{t('wk.missing', missing.length)}</span></div>}
          {formModules.length ? <AddTimesForm modules={formModules} /> : <p className="empty">{t('plan.emptySem')}</p>}
          <p className="fb-note">{t('wk.manualNote')}</p>
        </section>
      </div>
    </div>
  );
}
