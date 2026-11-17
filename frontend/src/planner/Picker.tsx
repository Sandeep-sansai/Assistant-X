import { useEffect, useState } from 'react';
import { useApp } from '../AppContext';
import { parseTermId, termLabel } from '../lib/dates';
import { useDialog } from '../lib/hooks';
import { CloseIcon } from '../components/Icons';
import { CatalogueList, catalogueGroups } from './Catalogue';

export interface PickRequest { index: number; replace: { code: string; requirement: string } | null }

/** The module catalogue for one semester; in swap mode only the replaced module's area and term. */
export function Picker({ request, onClose, onAdd, name }: { request: PickRequest | null; onClose: () => void; onAdd: (index: number, code: string, replace: string | null) => void; name: (code: string) => string }) {
  const app = useApp();
  const { t, planResult } = app;
  const ref = useDialog(Boolean(request), onClose);
  const [query, setQuery] = useState('');
  const [onlyMissing, setOnlyMissing] = useState(true);
  useEffect(() => {
    if (!request || !planResult) return;
    setQuery('');
    // With every area complete there is nothing "missing" to show; list everything instead.
    setOnlyMissing(!request.replace && planResult.requirements.some((q) => !q.met));
  }, [request, planResult]);
  const s = request && planResult ? planResult.semesters[request.index] : null;
  const term = s ? parseTermId(s.term) : null;
  const groups = s && request ? catalogueGroups(app, { winter: s.winter, term: s.term, query, onlyMissing, onlyTerm: Boolean(request.replace), onlyRequirement: request.replace && request.replace.requirement }) : [];
  return (
    <dialog className="wide" aria-labelledby="pkTitle" ref={ref}>
      {s && request && (
        <div className="dlg">
          <div className="jd-head">
            <h2 id="pkTitle">{request.replace ? t('wk.swapTitle', name(request.replace.code), term ? termLabel(t, term) : '') : t('plan.pickTitle', s.number, term ? termLabel(t, term) : '')}</h2>
            <button className="icon-btn" type="button" aria-label={t('aria.close')} onClick={onClose}><CloseIcon /></button>
          </div>
          <input className="pk-search" type="search" placeholder={t('plan.search')} value={query} onChange={(e) => setQuery(e.target.value)} autoFocus />
          <label className="pk-filter"><input type="checkbox" checked={onlyMissing} onChange={(e) => setOnlyMissing(e.target.checked)} /> <span>{t('plan.onlyMissing')}</span></label>
          <div className="pk-list">
            <CatalogueList groups={groups} onAdd={(code) => onAdd(request.index, code, request.replace && request.replace.code)} />
            {!groups.length && <p className="empty">{t('plan.pickNone')}</p>}
          </div>
        </div>
      )}
    </dialog>
  );
}
