#!/usr/bin/env bash
set -eu

gradle clean nativeCompile
chmod +x ./build/native/nativeCompile/mcp
mv ./build/native/nativeCompile/mcp ~/bin