#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/consumer-reuse.sh's decision, one case per line: reuse only on an exact match,
# a full Consumer run on a mismatch, a missing record, a forced run or a Central change.
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/consumer-reuse.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0
tree=0123456789abcdef0123456789abcdef01234567
other=fedcba9876543210fedcba9876543210fedcba98

record() { # name contents -> path
  printf '%s' "$2" > "$work/$1"
  echo "$work/$1"
}

expect() { # name expected-reuse env...
  local name=$1 want=$2
  shift 2
  local out
  out=$(env -i PATH="$PATH" "$@" "$script")
  local got=${out%%$'\n'*}
  if [[ "$got" == "reuse=$want" ]]; then echo "ok   $name"; else echo "FAIL $name: expected reuse=$want, got: $out"; failures=$((failures + 1)); fi
}

good=$(record good $'tree='$tree$'\nvulkan_publish=false\ntemplate_vulkan=0.1.60\nrun=42\n')
published=$(record published $'tree='$tree$'\nvulkan_publish=true\ntemplate_vulkan=\nrun=43\n')
stranger=$(record stranger $'tree='$other$'\nvulkan_publish=false\ntemplate_vulkan=0.1.60\nrun=44\n')
empty=$(record empty '')

expect "an exact match reuses" true TAG_TREE=$tree RECORD="$good" CURRENT_VULKAN=0.1.60
expect "a tag that publishes Vulkan matches without Central" true TAG_TREE=$tree RECORD="$published" VULKAN_PUBLISH=true
expect "another tree runs in full" false TAG_TREE=$tree RECORD="$stranger" CURRENT_VULKAN=0.1.60
expect "no record runs in full" false TAG_TREE=$tree RECORD= CURRENT_VULKAN=0.1.60
expect "an unreadable record runs in full" false TAG_TREE=$tree RECORD="$work/missing" CURRENT_VULKAN=0.1.60
expect "an empty record runs in full" false TAG_TREE=$tree RECORD="$empty" CURRENT_VULKAN=0.1.60
expect "a forced run runs in full" false FORCE=true TAG_TREE=$tree RECORD="$good" CURRENT_VULKAN=0.1.60
expect "a new Vulkan on Central runs in full" false TAG_TREE=$tree RECORD="$good" CURRENT_VULKAN=0.1.61
expect "an unreadable Central runs in full" false TAG_TREE=$tree RECORD="$good" CURRENT_VULKAN=
expect "a changed Vulkan publication choice runs in full" false TAG_TREE=$tree RECORD="$good" VULKAN_PUBLISH=true
expect "an unknown tag tree runs in full" false TAG_TREE= RECORD="$good" CURRENT_VULKAN=0.1.60

# consumer-reuse-find.sh against a stub `gh`: runs 9 (Consumer failed), 8 (another tree), 7 (this tree).
find="$(dirname "$script")/consumer-reuse-find.sh"
stub="$work/bin"
mkdir -p "$stub"
cat > "$stub/gh" <<'STUB'
#!/usr/bin/env bash
case "$*" in
  *"/workflows/build-and-publish.yml/runs"*) [[ -n "${STUB_NO_RUNS:-}" ]] && exit 1; printf '9\n8\n7\n' ;;
  *"/runs/9/jobs"*) echo failure ;;
  *"/jobs"*) echo success ;;
  "run download "*)
    run=$3; args="$*"; dir=${args##*--dir }
    case "$run" in
      8) printf 'tree=%s\nrun=8\n' "$STUB_OTHER" > "$dir/consumer-rehearsal.txt" ;;
      7) printf 'tree=%s\nrun=7\n' "$STUB_TREE" > "$dir/consumer-rehearsal.txt" ;;
      *) exit 1 ;;
    esac ;;
  *) exit 1 ;;
esac
STUB
chmod +x "$stub/gh"
found() { PATH="$stub:$PATH" STUB_TREE=$tree STUB_OTHER=$other "$@" "$find" owner/repo v1.2.3 "$tree" "$work/find"; }
got=$(found env)
if [[ "$got" == "$work/find/7/consumer-rehearsal.txt" ]]; then echo "ok   finds the green run that verified this tree"; else echo "FAIL find: got '$got'"; failures=$((failures + 1)); fi
got=$(found env STUB_NO_RUNS=1)
if [[ -z "$got" ]]; then echo "ok   an API failure finds nothing"; else echo "FAIL find on API failure: got '$got'"; failures=$((failures + 1)); fi

[[ $failures -eq 0 ]] || { echo "$failures case(s) failed"; exit 1; }
