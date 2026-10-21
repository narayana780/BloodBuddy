#!/usr/bin/env sh
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/.tools/apache-maven-3.9.9/bin/mvn" "$@"
