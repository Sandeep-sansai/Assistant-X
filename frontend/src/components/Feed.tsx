import { useState } from 'react';
import { useApp } from '../AppContext';
import { isNew, mineItems, reasonsFor } from '../lib/logic';
import { daysFromToday, fmt, parseDay } from '../lib/dates';
import { useReveal } from '../lib/hooks';
import type { FeedItem } from '../lib/types';
import { useDeptShort } from './Header';

function Item({ i, why, kind }: { i: FeedItem; why: string[]; kind: 'events' | 'news' }) {
  const { t, previousVisit } = useApp();
  const d = parseDay(i.date);
  return (
    <a className={`card item${i.department ? ' dept' : ''}`} href={i.url} target="_blank" rel="noopener">
      <div className="date-tile"><b>{d.getDate()}</b><small>{fmt(t, d, { month: 'short' }).replace('.', '')}</small></div>
      <div className="body">
        <div className="t">{i.title}</div>
        <div className="meta">
          <span className={`pill${i.department ? ' dept' : ''}`}>{i.source}</span>
          {why.map((w) => <span key={w} className="pill why">{t(`why.${w}`)}</span>)}
          {kind === 'events' && <span>{t('rel', daysFromToday(i.date))}</span>}
          {isNew(i, previousVisit) && <span className="pill new">{t('feed.new')}</span>}
        </div>
      </div>
      <span className="arrow">↗</span>
    </a>
  );
}

/** News and events: the student's department on the left, the whole university on the right. */
export function Feed() {
  const { t, feed, feedOk, profile } = useApp();
  const deptShort = useDeptShort();
  const [kind, setKind] = useState<'events' | 'news'>('events');
  const [showAll, setShowAll] = useState(false);
  const [ref] = useReveal<HTMLElement>();
  const dept = (profile && profile.department) || null;
  const items = kind === 'events' ? feed.events : feed.news;
  const limit = showAll ? Infinity : 5;
  const mine = dept ? mineItems(items, feed.departmentCovered, profile) : [];
  const uni = items.filter((i) => !i.department);
  return (
    <section aria-labelledby="feedTitle" ref={ref}>
      <div className="feed-head">
        <h2 id="feedTitle">{t('feed.title')}</h2>
        <div className="seg" role="group" aria-label={t('aria.feedType')}>
          {(['events', 'news'] as const).map((k) => (
            <button key={k} aria-pressed={kind === k} onClick={() => { setKind(k); setShowAll(false); }}>{t(`feed.${k}`)}</button>
          ))}
        </div>
      </div>
      <div className="feed-cols">
        <div className="feed-col mine">
          <h3>{t('feed.mine', dept ? deptShort(dept) : '')}</h3>
          <div>
            {!dept && <div className="note-box">{t('feed.pickDept')}</div>}
            {dept && !feed.departmentCovered && <div className="note-box">{t('feed.notCovered', deptShort(dept))}</div>}
          </div>
          <div className="items">
            {!feedOk ? <div className="empty">{t('feed.error')}</div>
              : dept && !mine.length ? <div className="empty">{t('feed.noMine')}</div>
              : mine.slice(0, limit).map(({ item, why }) => <Item key={item.url} i={item} why={why} kind={kind} />)}
          </div>
        </div>
        <div className="feed-col">
          <h3>{t('feed.across')}</h3>
          <div className="items">
            {feedOk && !uni.length && <div className="empty">{kind === 'events' ? t('feed.noEvents') : t('feed.noNews')}</div>}
            {feedOk && uni.slice(0, limit).map((i) => <Item key={i.url} i={i} why={reasonsFor(i, profile)} kind={kind} />)}
          </div>
        </div>
      </div>
      {(mine.length > 5 || uni.length > 5) && <button className="more" onClick={() => setShowAll(!showAll)}>{showAll ? t('feed.less') : t('feed.more')}</button>}
    </section>
  );
}
