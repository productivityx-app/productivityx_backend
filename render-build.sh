#!/usr/bin/env bash
set -e

echo "Building ProductivityX backend..."
./gradlew bootJar -x test --no-daemon
echo "Build complete."