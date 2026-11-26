#!/usr/bin/env bash
# Translates every jar in jars/ in N parallel translators (after one ./build.sh run built the runtime and translator).
set -euo pipefail
cd "$(dirname "$0")"
N="${N:-3}"
RCP="$PWD/runtime/target/classes:$(cat build/runtime.cp)"
ls jars/*.jar | awk -v n="$N" '{print > ("build/chunk" (NR % n))}'
for i in $(seq 0 $((N - 1))); do
  java -Xmx3g -cp "build/translator:$(cat build/translator.cp)" Driver "$RCP" out $(cat build/chunk$i) > build/batch$i.log 2>&1 &
done
wait
cat build/batch*.log | grep RESULT | sort > build/results.tsv
awk -F'\t' '{print $3}' build/results.tsv | sort | uniq -c
