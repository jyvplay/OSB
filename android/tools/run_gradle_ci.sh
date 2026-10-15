#!/usr/bin/env bash
# Preserve the actual Gradle exit code and expose bounded diagnostics through Checks API.
set -u
set +e
phase=${1:-}
case "$phase" in
  assemble|unit-tests|contracts|lint|device) ;;
  *) echo 'Expected a fixed CI phase: assemble, unit-tests, contracts, lint or device' >&2; exit 2 ;;
esac
shift
./gradlew --no-daemon --console=plain "$@" > "$phase.log" 2>&1
result=$?
cat "$phase.log"
if [ "$result" -ne 0 ]; then
  python3 tools/ci_annotations.py "$phase.log" "$phase"
fi
exit "$result"
