#!/usr/bin/env bash
# HTML work per page: JVM with JIT, JVM interpreter only (like OpenJ9 on iOS), JavaScriptCore with and without JIT
# (an iOS app gets no JIT), and native Rust. Usage: ./bench/run.sh page.html...   (after ../build.sh built the runtime)
set -euo pipefail
cd "$(dirname "$0")"
CP="$(cat ../build/runtime.cp):$(cat ../build/translator.cp)"
mkdir -p build out
javac -nowarn -proc:none -cp "$CP" -d build Bench.java BenchDriver.java
[ -f out/bench.js ] || java -cp "build:$CP" BenchDriver "build:../runtime/target/classes:$(cat ../build/runtime.cp)" out
(cd rust && cargo build -q --release)
pages=("$@")
echo "== JVM (JIT)";               java -cp "build:$CP" Bench "${pages[@]}"
echo "== JVM interpreter only";    java -Xint -Dn=5 -cp "build:$CP" Bench "${pages[@]}"
echo "== JavaScriptCore (JIT)";    bun bench.mjs out/bench.js "${pages[@]}"
echo "== JavaScriptCore (no JIT)"; N=5 BUN_JSC_useJIT=0 bun bench.mjs out/bench.js "${pages[@]}"
echo "== Rust (native)";           rust/target/release/html-bench "${pages[@]}"
