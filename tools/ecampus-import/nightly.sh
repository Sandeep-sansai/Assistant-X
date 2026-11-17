#!/bin/zsh
# Nightly timetable update (docs/decisions.md #20): imports the current and the next semester from the
# eCampus course catalogue and sends them to the app running on this machine. Run by launchd at 03:15
# (de.unikassel.assistant.timetable.plist). Logs go to ~/Library/Logs/uni-kassel-assistant-timetable.log.
set -u
DIR=${0:A:h}
TOKEN_FILE="$HOME/.uni-kassel-assistant/admin_token"
[[ -r $TOKEN_FILE ]] || { echo "$(date) no admin token at $TOKEN_FILE"; exit 1; }
export ADMIN_TOKEN=$(<"$TOKEN_FILE")
NODE=$(command -v node || echo /opt/homebrew/bin/node)
curl -sf -m 10 http://localhost:8080/api/semester >/dev/null || { echo "$(date) app not running, skipped"; exit 0; }

year=$(date +%Y); month=$((10#$(date +%m)))
if (( month >= 10 )); then terms=(WS$year SS$((year + 1)))
elif (( month <= 3 )); then terms=(WS$((year - 1)) SS$year)
else terms=(SS$year WS$year)
fi
for term in $terms; do
  echo "$(date) importing $term"
  "$NODE" "$DIR/import.mjs" --term "$term" --out "$HOME/.uni-kassel-assistant/timetable-$term.json" 2>&1
done
