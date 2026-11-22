#!/usr/bin/env bash
# Builds prebuilts/libs/framework-<version>.jar (compile-only stubs) from the dex2jar output of
# an AOSP framework.jar. See prebuilts/libs/README.md for where the input comes from.
#
# usage: prebuilts/tools/make-framework-jar.sh <dex2jar-output.jar> <android-version>
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "usage: $0 <dex2jar-output.jar> <android-version>" >&2
  exit 2
fi
input=$1
version=$2

root=$(cd "$(dirname "$0")/../.." && pwd)
cache=${XDG_CACHE_HOME:-$HOME/.cache}/fugueti-prebuilts
mkdir -p "$cache"

asm_version=9.7.1
asm_sha256=8cadd43ac5eb6d09de05faecca38b917a040bb9139c7edeb4cc81c740b713281
asm_jar=$cache/asm-$asm_version.jar
if ! echo "$asm_sha256  $asm_jar" | sha256sum -c --status 2>/dev/null; then
  curl -fsSL -o "$asm_jar" \
    "https://repo1.maven.org/maven2/org/ow2/asm/asm/$asm_version/asm-$asm_version.jar"
  echo "$asm_sha256  $asm_jar" | sha256sum -c --quiet
fi

output=$root/prebuilts/libs/framework-$version.jar
java -cp "$asm_jar" "$root/prebuilts/tools/StubJar.java" "$input" "$output"
sha256sum "$output"
