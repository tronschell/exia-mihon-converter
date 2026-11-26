#!/usr/bin/env bash
# Translates Tachiyomi/Mihon extension jars to JavaScript with TeaVM.
# Usage: ./build.sh <extension.jar>...   (writes out/<name>.js and out/<name>.problems.txt)
# OBF=0 keeps readable names. Needs JDK 17+; Maven comes from ./mvnw.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/translator out
./mvnw -q -B -f runtime/pom.xml compile dependency:build-classpath -Dmdep.outputFile="$PWD/build/runtime.cp"
./mvnw -q -B -f translator/pom.xml dependency:build-classpath -Dmdep.outputFile="$PWD/build/translator.cp"
javac -nowarn -cp "$(cat build/translator.cp)" -d build/translator translator/*.java
cp -R translator/META-INF build/translator/
RCP="$PWD/runtime/target/classes:$(cat build/runtime.cp)"
java -cp "build/translator:$(cat build/translator.cp)" Driver "$RCP" out "$@"
