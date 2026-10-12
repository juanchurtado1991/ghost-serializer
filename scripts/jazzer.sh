#!/usr/bin/env bash
# Coverage-guided fuzzing of the JSON readers and writers with Jazzer.
#
#   scripts/jazzer.sh [per-test duration] [file filter]
#
# Jazzer's JUnit mode fuzzes only one @FuzzTest per JVM, so each test method gets its own Gradle run, one after the
# other, for the given duration (default 2m; Jazzer accepts 90s, 30m, 8h ...). The default filter selects the four
# GhostJson*FuzzTest classes (10 tests): `scripts/jazzer.sh 2m` takes about 25 minutes and `scripts/jazzer.sh 45m`
# about 7.5 hours. Pass another regex over the file path to fuzz other classes, e.g. '/proto/'.
#
# maxDuration can only be set in the annotation, so it is rewritten for the run and the sources are restored when
# the script ends, even on Ctrl+C.
#
# Where things end up:
#   build/jazzer/<timestamp>/summary.txt        one line per test: PASS/FAIL, executions, duration
#   build/jazzer/<timestamp>/<Class>.<method>.log   full Gradle + Jazzer output of that test
#   build/jazzer/<timestamp>/<Class>.<method>.xml   its JUnit result file
#   ghost-serialization/.cifuzz-corpus/             inputs Jazzer found interesting (tracked; replayed by every jvmTest)
#   ghost-serialization/src/jvmTest/resources/      crash inputs (<package>/<Class>Inputs/<method>/crash-*), if any
# Commit new corpus files and any crash input together with the fix.
set -euo pipefail

cd "$(dirname "$0")/.."

duration="${1:-2m}"
filter="${2:-/GhostJson[A-Za-z]*FuzzTest\.kt$}"

all_fuzz_files=$(grep -rlE '^    @FuzzTest$' ghost-serialization/src/jvmTest --include='*FuzzTest.kt')
if [ -n "$(git status --porcelain -- $all_fuzz_files)" ]; then
  echo "Uncommitted changes in the fuzz test files; commit or stash them first (they are restored with git checkout)." >&2
  exit 1
fi

restore() { git checkout -- $all_fuzz_files; }
trap restore EXIT

for file in $all_fuzz_files; do
  sed -i -E "s/^    @FuzzTest$/    @FuzzTest(maxDuration = \"${duration}\")/" "$file"
done

report_dir="build/jazzer/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$report_dir"
summary="$report_dir/summary.txt"
echo "Jazzer ${duration} per test, started $(date -Iseconds)" > "$summary"
echo "Report: $report_dir"

failed=0
total=0
for file in $(echo "$all_fuzz_files" | grep -E "$filter"); do
  package=$(sed -n 's/^package //p' "$file")
  class_name=$(basename "$file" .kt)
  for method in $(grep -A1 '@FuzzTest' "$file" | sed -n 's/.*fun \([A-Za-z0-9_]*\)(.*/\1/p'); do
    total=$((total + 1))
    name="${class_name}.${method}"
    log="$report_dir/${name}.log"
    echo "=== ${package}.${name} for ${duration}"
    if JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests "${package}.${name}" --rerun --console=plain > "$log" 2>&1; then
      status=PASS
    else
      status=FAIL
      failed=$((failed + 1))
      echo "!!! ${name} FAILED, see ${log}"
    fi
    runs=$(grep -oE 'Done [0-9]+ runs in [0-9]+ second' "$log" | tail -1 || true)
    cp ghost-serialization/build/test-results/jvmTest/*"${class_name}".xml "$report_dir/${name}.xml" 2>/dev/null || true
    echo "${status}  ${name}  ${runs:-no run summary found}" | tee -a "$summary"
  done
done

{
  echo "Fuzzed ${total} tests, ${failed} failed."
  git status --short -- ghost-serialization/.cifuzz-corpus | wc -l | xargs echo "Corpus files added or changed:"
  echo "Crash inputs:"
  git status --short -- ghost-serialization/src/jvmTest/resources | grep -E 'Inputs|crash-' || echo "  none"
} | tee -a "$summary"
[ "$failed" -eq 0 ]
