# Request: past module data for the study planner

**To:** Studierendenservice / Prüfungsamt FB 16 / ITS, University of Kassel
**From:** the Study Assistant student project
**Purpose:** make the study planner's suggestions realistic, e.g. warn when a module was not offered
recently or is only offered every other year.

## What we ask for

Anonymised, aggregated numbers per module and semester for the modules of the **Computer Science
M.Sc. (PO 2026)** and its predecessor **Informatik M.Sc. (PO 2018)**, for as many past semesters as
are available (ideally WS 2020/21 onwards). **No personal data**: only totals, no names,
matriculation numbers or individual grades.

| Column | Example | Meaning |
|---|---|---|
| `module_code` | `PI-11` | Module code as in the module database |
| `semester` | `WS 2024/25` or `SS 2025` | Semester |
| `offered` | `yes` / `no` | Whether the module was taught that semester |
| `enrolled` | `85` | Students registered for the exam (optional) |
| `passed` | `70` | Students who passed (optional) |
| `avg_grade` | `2.3` | Average grade of those who passed (optional) |

A CSV (comma or semicolon separated, first line = column names) is ideal. Numbers below a minimum
group size (e.g. fewer than 5 students) can be left empty to protect privacy.

Optionally, and also only as totals: how many semesters students actually needed to finish the
master's, per start year.

## How the app uses it

Set `PLANNER_HISTORY_FILE=/path/to/file.csv` and restart (or trigger
`POST /api/admin/programmes/import`). The planner then:

- prefers modules with a good pass rate when several fit equally well,
- avoids modules that were not offered in the recent semesters of the data and warns if a plan uses them,
- reads winter/summer offering from the data when a module description does not state it.

Nothing is shown about individual semesters or students; only these per-module summaries.
