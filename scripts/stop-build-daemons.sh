#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Stops every Gradle and Kotlin daemon on the runner, waits until they have exited, and prints the
# memory that left free (#1592). Run it between builds that don't share a daemon.
#
# A build's daemons outlive it, holding their heap. Each template sets its own daemon heap, so no build
# reuses another's daemon: on a passing Templates run (#1847) the root publish's daemons still held
# about 9.6 GB when the templates started, the templates' daemons came on top, and the runner fell to
# 617 MiB available with 3 GB swapped. The runner losses on 20 and 21 Nov happened in those steps.
#
#   scripts/stop-build-daemons.sh
#
# `gradle --stop` stops every daemon of its Gradle version under GRADLE_USER_HOME, so the root
# wrapper's reaches every build here, templates included (they pin the same version). A Kotlin daemon
# is stopped by its process: it would only exit once idle. STOP_DAEMONS_WAIT is how many seconds to
# wait for them to exit (default 60); a daemon still running after it fails the script.
# GRADLE_DAEMON and KOTLIN_DAEMON, the patterns their command lines match, are set only by the test.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gradle="${GRADLE_DAEMON:-org.gradle.launcher.daemon.bootstrap.GradleDaemon}"
kotlin="${KOTLIN_DAEMON:-org.jetbrains.kotlin.daemon.KotlinCompileDaemon}"
daemons="$gradle|$kotlin"

(cd "$root" && ./gradlew --stop -q) || true
pkill -f "$kotlin" || true

for ((waited = 0; waited < ${STOP_DAEMONS_WAIT:-60}; waited++)); do
  pgrep -f "$daemons" > /dev/null || break
  sleep 1
done
if pgrep -f "$daemons" > /dev/null; then
  echo "::error::Build daemons still running after ${STOP_DAEMONS_WAIT:-60} s:"
  pgrep -af "$daemons" | cut -c1-200
  exit 1
fi
echo "Build daemons stopped; $(awk '/^MemAvailable:/ { print int($2 / 1024) }' /proc/meminfo) MiB available"
