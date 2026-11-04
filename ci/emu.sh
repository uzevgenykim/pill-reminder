#!/usr/bin/env bash
# Запускает инструментальные тесты на эмуляторе и сохраняет логи для отчёта.
mkdir -p /tmp/ci
gradle connectedDebugAndroidTest --stacktrace 2>&1 | tee /tmp/ci/gradle.log
echo "${PIPESTATUS[0]}" > /tmp/ci/exit_code
adb logcat -d -b crash > /tmp/ci/crash.log 2>&1
adb logcat -d AndroidRuntime:E ActivityManager:E '*:S' > /tmp/ci/runtime.log 2>&1
exit 0
