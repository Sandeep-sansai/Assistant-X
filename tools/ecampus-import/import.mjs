#!/usr/bin/env node
// Imports weekly course times for one programme and semester from the public eCampus course
// catalogue and sends them to the app (POST /api/admin/timetable). Runs on the maintainer's
// machine with the IT service's permission; see docs/decisions.md #20 for the limits it keeps:
// public pages only, one programme, slow, at most once a day.
//
//   ADMIN_TOKEN=… node tools/ecampus-import/import.mjs --term WS2026 [--dry-run] [--api http://localhost:8080]
//
// Needs Google Chrome; drives it headless over the DevTools protocol like a person using the page
// (eCampus is a JavaScript app, so plain HTTP requests don't work). It reads the public
// "Tagesaktuelle Veranstaltungen" list for the five days of one lecture week: about 5–15 page loads.
import { spawn } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, all) => {
  if (a.startsWith('--')) acc.push([a.slice(2), all[i + 1] && !all[i + 1].startsWith('--') ? all[i + 1] : true]);
  return acc;
}, []));
const TERM = args.term;                          // WS2026 or SS2027
const PROGRAMME = args.programme || 'cs-msc-2026';
const API = args.api || 'http://localhost:8080';
const DRY = Boolean(args['dry-run']);
const DELAY = Math.max(2000, Number(args.delay || 3000));   // never faster than one page every 2 s
if (!/^(WS|SS)\d{4}$/.test(TERM || '')) { console.error('Usage: --term WS2026|SS2027 [--dry-run]'); process.exit(2); }
if (!DRY && !process.env.ADMIN_TOKEN) { console.error('Set ADMIN_TOKEN (or use --dry-run).'); process.exit(2); }

const USER_AGENT = 'UniKasselStudyAssistant/0.1 (student project; course catalogue import with ITS permission)';
const DAYS = { mo: 1, montag: 1, di: 2, dienstag: 2, mi: 3, mittwoch: 3, do: 4, donnerstag: 4, fr: 5, freitag: 5, sa: 6, samstag: 6, so: 7, sonntag: 7,
  mon: 1, monday: 1, tue: 2, tuesday: 2, wed: 3, wednesday: 3, thu: 4, thursday: 4, fri: 5, friday: 5, sat: 6, saturday: 6, sun: 7, sunday: 7 };

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const port = 9400 + Math.floor(Math.random() * 400);
const chrome = spawn('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  ['--headless=new', `--remote-debugging-port=${port}`, '--no-first-run', `--user-data-dir=${mkdtempSync(join(tmpdir(), 'ecampus-'))}`,
   `--user-agent=${USER_AGENT}`, 'about:blank'], { stdio: 'ignore' });
let targets;
for (let i = 0; i < 50; i++) { try { targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json(); break; } catch { await sleep(200); } }
const ws = new WebSocket(targets.find((t) => t.type === 'page').webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener('open', r));
let seq = 0; const pending = new Map();
ws.addEventListener('message', (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } });
const cmd = (method, params = {}) => new Promise((r) => { const i = ++seq; pending.set(i, r); ws.send(JSON.stringify({ id: i, method, params })); });
const run = async (expr) => {
  const r = await cmd('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true });
  if (r.result.exceptionDetails) throw new Error(r.result.exceptionDetails.exception?.description || 'page script failed');
  return r.result.result.value;
};
const open = async (url) => { await cmd('Page.navigate', { url }); await sleep(DELAY + 1500); };
const log = (...a) => console.error('[import]', ...a);
// Pages are still loading for a moment after navigation; errors then just mean "not yet".
const waitFor = async (expr, tries = 30) => {
  for (let i = 0; i < tries; i++) {
    try { if (await run(`(() => { try { return ${expr}; } catch { return false; } })()`)) return true; } catch { /* page not ready */ }
    await sleep(750);
  }
  return false;
};

/** Mon–Fri of the third lecture week (WS: from mid-October, SS: from mid-April), or of --week YYYY-MM-DD. */
function referenceWeek(term, given) {
  let monday;
  if (given) monday = new Date(`${given}T12:00:00`);
  else {
    const year = Number(term.slice(2));
    monday = new Date(term.startsWith('WS') ? `${year}-10-15T12:00:00` : `${year}-04-15T12:00:00`);
    while (monday.getDay() !== 1) monday.setDate(monday.getDate() + 1);
    monday.setDate(monday.getDate() + 7);
  }
  return [0, 1, 2, 3, 4].map((i) => { const d = new Date(monday); d.setDate(d.getDate() + i); return d; });
}

try {
  await cmd('Page.enable');
  // One typical lecture week (Mon–Fri) of the semester: every course meeting on each day is listed
  // on the public "Tagesaktuelle Veranstaltungen" page. Reading five days gives the weekly timetable.
  const week = referenceWeek(TERM, args.week);
  log('reference week', week.map((d) => d.toISOString().slice(0, 10)).join(', '));
  const DAY_PAGE = 'https://ecampus.uni-kassel.de/qisserver/pages/cm/exa/timetable/currentLectures.xhtml?_flowId=showEventsAndExaminationsOnDate-flow&navigationPosition=studiesOffered,currentLectures&recordRequest=true';
  const F = 'showEventsAndExaminationsOnDateForm';
  const byCourse = new Map();
  for (const date of week) {
    const label = `${String(date.getDate()).padStart(2, '0')}.${String(date.getMonth() + 1).padStart(2, '0')}.${date.getFullYear()}`;
    await open(DAY_PAGE);
    await waitFor(`!!document.getElementById('${F}:tabContainer:date-selection-container:date_input')`);
    await run(`(() => { const i = document.getElementById('${F}:tabContainer:date-selection-container:date_input');
      i.value = '${label}'; i.dispatchEvent(new Event('change', { bubbles: true })); document.getElementById('${F}:searchButtonId').click(); return true; })()`);
    await waitFor(`(document.body.innerText.match(/Suchergebnisse für [^\\n]*/) || [''])[0].includes('${date.getDate()}.')`, 40);
    await sleep(DELAY);
    // Show all of the day's rows on one page.
    await run(`(() => { const n = document.querySelector('input[id$="Navi2NumRowsInput"]'); if (!n) return false;
      n.value = '300'; n.dispatchEvent(new Event('change', { bubbles: true }));
      document.querySelector('button[id$="Navi2NumRowsRefresh"]')?.click(); return true; })()`);   // the page allows at most 300
    await sleep(DELAY + 2000);
    if (args.debug) {
      console.log(await run(`JSON.stringify({
        firstRows: [...document.querySelectorAll('table[id$="coursesAndExaminationsOnDateListTableTable"] tbody tr')].slice(0, 2).map(tr => [...tr.children].map(td => td.innerText)),
        head: [...document.querySelectorAll('table[id$="coursesAndExaminationsOnDateListTableTable"] thead th')].map(th => th.innerText),
        navi: [...document.querySelectorAll('[id*="coursesAndExaminationsOnDateListTableNavi"]')].map(e => e.tagName + '#' + e.id + ' | ' + (e.innerText || e.value || e.title || e.getAttribute('aria-label') || '').replace(/\\s+/g, ' ').trim().slice(0, 50)).slice(0, 40) }, null, 1)`));
      process.exit(0);
    }
    let rows = [];
    for (let pageNo = 1; pageNo <= 20; pageNo++) {
      rows = rows.concat(JSON.parse(await run(`(() => {
        const table = document.querySelector('table[id$="coursesAndExaminationsOnDateListTableTable"]');
        if (!table) return '[]';
        const clean = (t) => (t || '').replace(/\\[Sortierbare Spalte\\]/g, '').replace(/\\s+/g, ' ').trim();
        const head = [...table.querySelectorAll('thead th')].map(th => clean(th.innerText));
        const col = (re) => head.findIndex(h => re.test(h));
        const c = { title: col(/^Titel/), start: col(/^Beginn/), end: col(/^Ende/), number: col(/^Nummer/), group: col(/^Parallelgruppe/),
          kind: col(/^Veranstaltungsart/), room: col(/^Räume/), term: col(/^Semester/), remark: col(/^Bemerkung/) };
        return JSON.stringify([...table.querySelectorAll('tbody tr')].map(tr => {
          // On narrow layouts each cell repeats its column name before the value: drop that label.
          const cells = [...tr.children].map((td, i) => { let t = clean(td.innerText); const h = head[i] || '';
            if (h && t.startsWith(h)) t = t.slice(h.length).trim(); return t; });
          const get = (k) => c[k] >= 0 ? cells[c[k]] : '';
          return { title: get('title'), start: get('start'), end: get('end'), number: get('number'), group: get('group'),
            kind: get('kind'), room: get('room').replace(/^Räume \\(Gebäude\\)/, ''), term: get('term'), remark: get('remark') };
        }));
      })()`)));
      const paging = await run(`(document.body.innerText.match(/Seite (\\d+) von (\\d+)/) || []).slice(1).join('/')`);
      const [cur, total] = (paging || '1/1').split('/').map(Number);
      if (!total || cur >= total) break;
      const next = await run(`(() => { const b = document.querySelector('a[id$="coursesAndExaminationsOnDateListTableNavi2next"]'); if (!b) return false; b.click(); return true; })()`);
      if (!next) { log('no next-page button on', label, 'after page', cur, 'of', total); break; }
      await sleep(DELAY + 1500);
    }
    const weekday = ((date.getDay() + 6) % 7) + 1;
    let kept = 0;
    for (const r of rows) {
      if (!r.title || !/^\d{1,2}:\d{2}$/.test(r.start) || !/^\d{1,2}:\d{2}$/.test(r.end)) continue;
      if (/abgesagt|ausfall|cancel/i.test(r.remark)) continue;
      const key = `${r.number}|${r.title}`;
      if (!byCourse.has(key)) byCourse.set(key, { title: r.title, number: r.number, url: DAY_PAGE, slots: [] });
      const slot = { kind: r.kind || null, group: r.group || null, weekday, start: r.start.padStart(5, '0'), end: r.end.padStart(5, '0'), room: r.room || null };
      const course = byCourse.get(key);
      if (!course.slots.some((x) => x.weekday === slot.weekday && x.start === slot.start && x.group === slot.group)) { course.slots.push(slot); kept++; }
    }
    log(label, `${rows.length} meetings read, ${kept} new slots`);
  }
  // Number and title together let the app match courses to the programme's modules.
  // Parallel groups ("1. PG", "2. PG") of the same type are alternatives; the type then names the group.
  const courses = [...byCourse.values()].map((c) => {
    const groupsPerKind = {};
    for (const x of c.slots) (groupsPerKind[x.kind] ||= new Set()).add(x.group);
    return { title: c.number ? `${c.title} ${c.number}` : c.title, url: c.url,
      slots: c.slots.map(({ group, ...x }) => ({ ...x, kind: groupsPerKind[x.kind].size > 1 && group ? `${x.kind || ''} · ${group}`.trim() : x.kind })) };
  });
  log(`${courses.length} courses with weekly slots`);
  const body = { programme: PROGRAMME, term: TERM, courses };
  const out = args.out || join(process.cwd(), `timetable-${PROGRAMME}-${TERM}.json`);
  writeFileSync(out, JSON.stringify(body, null, 2));
  log('saved', out);
  if (DRY) {
    log('dry run: nothing sent');
  } else {
    const res = await fetch(`${API}/api/admin/timetable`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Admin-Token': process.env.ADMIN_TOKEN }, body: JSON.stringify(body) });
    log('app answered', res.status, await res.text());
  }
} catch (err) {
  console.error('[import] failed:', err.message);
  process.exitCode = 1;
} finally {
  ws.close();
  chrome.kill();
}
