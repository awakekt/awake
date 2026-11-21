#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Samples a CI runner's memory and disk every few seconds, and reports the worst it saw (#1592).
# Template-building steps lost their runner three times on 20–21 Nov, with no log; this names what
# the runner was short of.
#
#   scripts/runner-memory.sh start <log>     # samples in the background, until the job ends
#   scripts/runner-memory.sh report <log>    # prints the peak, the low points and the busiest moment
#
# Each sample is one line: the UTC time, MemAvailable MiB, swap used MiB, the summed RSS of every java
# process in MiB, how many Gradle daemons and Kotlin daemons run, and the free disk on / in MiB.
# RUNNER_MEMORY_INTERVAL sets the seconds between samples (default 5).
set -euo pipefail

sample() {
  local avail swap_total swap_free java gradle kotlin disk
  avail=$(awk '/^MemAvailable:/ { print int($2 / 1024) }' /proc/meminfo)
  swap_total=$(awk '/^SwapTotal:/ { print $2 }' /proc/meminfo)
  swap_free=$(awk '/^SwapFree:/ { print $2 }' /proc/meminfo)
  java=$({ ps -C java -o rss= 2> /dev/null || true; } | awk '{ s += $1 } END { print int(s / 1024) }')
  gradle=$(pgrep -fc 'org.gradle.launcher.daemon.bootstrap.GradleDaemon' || true)
  kotlin=$(pgrep -fc 'org.jetbrains.kotlin.daemon.KotlinCompileDaemon' || true)
  disk=$(df -Pm / | awk 'NR == 2 { print $4 }')
  echo "$(date -u +%H:%M:%S) $avail $(((swap_total - swap_free) / 1024)) $java $gradle $kotlin $disk"
}

case "${1:-}" in
  start)
    log="${2:?usage: runner-memory.sh start <log>}"
    : > "$log"
    nohup bash -c "while true; do \"$0\" sample >> \"$log\"; sleep ${RUNNER_MEMORY_INTERVAL:-5}; done" > /dev/null 2>&1 &
    echo "Sampling the runner's memory into $log (pid $!)"
    ;;
  sample) sample ;;
  report)
    log="${2:?usage: runner-memory.sh report <log>}"
    [[ -s "$log" ]] || { echo "No samples in $log"; exit 0; }
    awk '
      { n++ }
      NR == 1 || $2 < minAvail { minAvail = $2; minAvailAt = $0 }
      NR == 1 || $4 > maxJava { maxJava = $4; maxJavaAt = $0 }
      NR == 1 || $5 + $6 > maxDaemons { maxDaemons = $5 + $6; maxDaemonsAt = $0 }
      NR == 1 || $7 < minDisk { minDisk = $7 }
      $3 > maxSwap { maxSwap = $3 }
      END {
        printf "Samples: %d\n", n
        printf "Lowest MemAvailable: %d MiB (sample: %s)\n", minAvail, minAvailAt
        printf "Highest java RSS: %d MiB (sample: %s)\n", maxJava, maxJavaAt
        printf "Most daemons at once: %d Gradle + Kotlin (sample: %s)\n", maxDaemons, maxDaemonsAt
        printf "Most swap used: %d MiB\n", maxSwap
        printf "Least free disk on /: %d MiB\n", minDisk
        print "Columns: UTC time, MemAvailable MiB, swap MiB, java RSS MiB, Gradle daemons, Kotlin daemons, free disk MiB"
      }' "$log"
    echo "Once a minute:"
    awk -v every="$(( 60 / ${RUNNER_MEMORY_INTERVAL:-5} ))" 'NR % every == 1' "$log"
    ;;
  *) echo "usage: runner-memory.sh start|report <log>" >&2; exit 64 ;;
esac
