#!/usr/bin/env bash
# Runs every translated extension in out/ against its real site: popular, search, details, chapters, pages, image.
# Usage: P=6 JIT=0 ./harness/run-all.sh results.jsonl   (JIT=0 runs JavaScriptCore without its JIT, as in an iOS app)
cd "$(dirname "$0")/.."
OUT="${1:-build/run.jsonl}"
P="${P:-6}"
: > "$OUT"
one() {
  f="$1"
  if [ "${JIT:-1}" = "0" ]; then export BUN_JSC_useJIT=0; fi
  r=$(perl -e 'alarm 150; exec @ARGV' bun harness/host.mjs "$f" a 2>/dev/null | tail -1)
  case "$r" in "{"*) echo "$r" ;; *) echo "{\"file\":\"$(basename "$f")\",\"error\":\"timeout or crash\"}" ;; esac
}
export -f one
ls out/*.js | xargs -P "$P" -I{} bash -c 'one "$@"' _ {} >> "$OUT"
wc -l "$OUT"
