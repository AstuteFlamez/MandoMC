#!/usr/bin/env bash
# Source this file from a repository script. A project-local JDK avoids changing system defaults.
MANDOMC_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n "${MANDOMC_JAVA_HOME:-}" ]]; then
    JAVA_HOME="$MANDOMC_JAVA_HOME"
elif [[ -x "$MANDOMC_ROOT/.local/jdks/jdk-25.0.5+8/Contents/Home/bin/java" ]]; then
    JAVA_HOME="$MANDOMC_ROOT/.local/jdks/jdk-25.0.5+8/Contents/Home"
elif [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "Set MANDOMC_JAVA_HOME to a Java 25 JDK." >&2
    return 1
fi
MANDOMC_JAVA_VERSION="$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
if [[ ! "$MANDOMC_JAVA_VERSION" =~ \"25\. ]]; then
    echo "Java 25 is required. Found: $MANDOMC_JAVA_VERSION" >&2
    return 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
