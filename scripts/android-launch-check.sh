#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Installs an APK on the connected device, launches it, and fails unless it is still running a while
# later with no JNI lookup failing in its log. The nightly device tests run it on the engine
# showcase's shrunk release, whose startup creates a Jolt physics world and a Vulkan device: a class,
# field or method that native code finds by name and R8 renamed aborts it there.
#
#   scripts/android-launch-check.sh <apk> <package>/<activity> [seconds]
#
# Uses adb from the PATH, or $ADB, with one device connected.
set -euo pipefail

usage="usage: android-launch-check.sh <apk> <package>/<activity> [seconds]"
apk="${1:?$usage}"
component="${2:?$usage}"
seconds="${3:-20}"
adb="${ADB:-adb}"
package="${component%%/*}"
lookups='JNI DETECTED ERROR|NoSuchFieldError|NoSuchMethodError|ClassNotFoundException|UnsatisfiedLinkError'

"$adb" install -r "$apk" >/dev/null
"$adb" logcat -c
"$adb" shell am start -W -n "$component" >/dev/null
sleep "$seconds"

pid="$("$adb" shell pidof "$package" | tr -d '\r' || true)"
if [ -z "$pid" ]; then
  echo "✗ $package died within ${seconds}s of launch:" >&2
  "$adb" logcat -d | grep -E "FATAL EXCEPTION|Process: $package|Fatal signal|>>> $package <<<|Abort message|$lookups" >&2 || true
  # The native crash's backtrace, to tell a missing name from a driver fault.
  "$adb" logcat -d | grep -E " F DEBUG +: +(#[0-9]+ pc|backtrace|signal|Cause)" | head -n 40 >&2 || true
  exit 1
fi

failed="$("$adb" logcat -d --pid="$pid" | grep -E "$lookups" || true)"
if [ -n "$failed" ]; then
  echo "✗ $package is running, but native code failed to find what it looks up by name:" >&2
  echo "$failed" >&2
  exit 1
fi
echo "✓ $package is running ${seconds}s after launch"
