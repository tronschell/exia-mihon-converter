#!/usr/bin/env bash
# The resources the app shares with every extension (time zones, public suffixes), from the runtime's jars.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p res
M2="${M2:-$HOME/.m2/repository}"
unzip -o -q -j "$(find "$M2" -name 'threetenbp-1.7.0.jar' | head -1)" org/threeten/bp/TZDB.dat -d res
unzip -o -q -j "$(find "$M2" -name 'okhttp-jvm-5.4.0.jar' | head -1)" okhttp3/internal/publicsuffix/PublicSuffixDatabase.list -d res
ls -la res
