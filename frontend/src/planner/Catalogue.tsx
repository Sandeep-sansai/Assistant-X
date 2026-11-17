import { useApp } from '../AppContext';
import type { Module, Requirement } from '../lib/types';
import { OFFERED_BADGE, shortRequirement, uiTitle } from './names';
import { DAYS, hhmm, semesterSlots } from './timetable';

type App = ReturnType<typeof useApp>;
export interface CatalogueGroup { req: Requirement; modules: { m: Module; fits: boolean; times: string }[] }

/**
 * Modules not planned yet, grouped by requirement. {@code onlyTerm} hides modules not taught in that
 * semester; {@code onlyMissing} hides requirements that are already met.
 */
export function catalogueGroups(app: App, { winter, term, query, onlyMissing, onlyTerm, onlyRequirement }: {
  winter: boolean; term?: string; query: string; onlyMissing: boolean; onlyTerm: boolean; onlyRequirement?: string | null;
}): CatalogueGroup[] {
  const { t, catalogue, plan, planResult, timetable, manualSlots } = app;
  if (!catalogue || !plan || !planResult) return [];
  const programme = catalogue.programme;
  const planned = new Set([...plan.semesters.flat(), ...plan.completed]);
  const q = query.trim().toLowerCase();
  const groups: CatalogueGroup[] = [];
  for (const req of planResult.requirements) {
    if (onlyMissing && req.met) continue;
    if (onlyRequirement && req.code !== onlyRequirement) continue;
    const group = programme.groups.find((g) => g.code === req.code);
    const codes = new Set(group ? group.modules : []);
    // A focus requirement without its own list (e.g. "Advanced courses") takes the modules of its parts.
    if (!codes.size) programme.groups.filter((g) => g.code.startsWith(`${req.code}.`)).forEach((g) => g.modules.forEach((c) => codes.add(c)));
    const modules = [...codes].map((c) => programme.modules[c]).filter((m): m is Module => {
      if (!m || planned.has(m.code)) return false;
      const fits = m.offered === 'BOTH' || m.offered === 'UNKNOWN' || (m.offered === 'WINTER') === winter;
      if (onlyTerm && !fits) return false;
      return !q || m.title.toLowerCase().includes(q) || uiTitle(app, m.title).toLowerCase().includes(q) || m.code.toLowerCase().includes(q);
    }).map((m) => ({
      m,
      fits: m.offered === 'BOTH' || m.offered === 'UNKNOWN' || (m.offered === 'WINTER') === winter,
      times: term ? semesterSlots(timetable, manualSlots, { term, modules: [{ code: m.code }] }).slots
        .map((x) => `${t(`day.${DAYS[x.weekday]}`)} ${hhmm(x.start)}–${hhmm(x.end)}`).join(', ') : '',
    }));
    if (modules.length) groups.push({ req, modules });
  }
  return groups;
}

export function CatalogueList({ groups, onAdd }: { groups: CatalogueGroup[]; onAdd: (code: string) => void }) {
  const app = useApp();
  const { t } = app;
  return (
    <>
      {groups.map(({ req, modules }) => (
        <section className="pk-group" key={req.code}>
          <h3>{`${shortRequirement(app, req.title)} · ${req.planned}/${req.required} CP`}</h3>
          {modules.map(({ m, fits, times }) => (
            <div className="pk-row" key={m.code}>
              <div>
                <span className="pk-title">{uiTitle(app, m.title)}</span>
                <span className="pk-meta">{`${m.credits} CP · ${t(OFFERED_BADGE[m.offered] || 'plan.unknown')}${m.oralExam ? ' · 🎤' : ''}${times ? ` · ${times}` : ''}${fits ? '' : ` · ⚠ ${t('plan.notThisTerm')}`}`}</span>
              </div>
              <button className="btn" type="button" onClick={() => onAdd(m.code)}>{t('plan.addShort')}</button>
            </div>
          ))}
        </section>
      ))}
    </>
  );
}
