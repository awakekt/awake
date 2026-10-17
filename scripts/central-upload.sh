#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Uploads a bundle from scripts/central-bundle.sh to Maven Central as one deployment and waits for
# Central's verdict, through the Publisher API (https://central.sonatype.org/publish/publish-portal-api/).
#
#   scripts/central-upload.sh build/central-bundle.zip "awake v0.5.0"               # publish
#   scripts/central-upload.sh build/central-bundle.zip "awake check" USER_MANAGED   # validate, then drop
#
# AUTOMATIC publishes once the bundle validates and returns when Central starts publishing, which
# cannot be undone. When Central already lists the bundle's version as published, AUTOMATIC uploads
# nothing and succeeds, so a release job can be re-run. USER_MANAGED stops at validation and drops
# the deployment, so nothing is published. A deployment that fails validation is left in place: it
# publishes nothing, and Central support needs it kept to look into the refusal. Reads
# MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD, a Central user token.
set -euo pipefail

usage="usage: central-upload.sh <bundle.zip> <deployment-name> [AUTOMATIC|USER_MANAGED]"
bundle="${1:?$usage}"
name="${2:?$usage}"
type="${3:-AUTOMATIC}"
api="https://central.sonatype.com/api/v1/publisher"
poll_seconds="${CENTRAL_POLL_SECONDS:-15}"
timeout_seconds="${CENTRAL_TIMEOUT_SECONDS:-2700}"

case "$type" in AUTOMATIC | USER_MANAGED) ;; *) echo "$usage" >&2 && exit 1 ;; esac
[[ -f "$bundle" ]] || { echo "No bundle at $bundle" >&2; exit 1; }
if [[ -z "${MAVEN_CENTRAL_USERNAME:-}" || -z "${MAVEN_CENTRAL_PASSWORD:-}" ]]; then
  echo "MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD must hold a Central user token." >&2
  exit 1
fi
auth="Authorization: Bearer $(printf '%s:%s' "$MAVEN_CENTRAL_USERNAME" "$MAVEN_CENTRAL_PASSWORD" | base64 | tr -d '\n')"
central() { curl --fail-with-body --silent --show-error --header "$auth" "$@"; }
uri() { jq -rn --arg v "$1" '$v|@uri'; }

# A re-run of a release job whose upload already went out must not send the same version again:
# Central refuses a second deployment of a published version, and that refusal would fail the job
# before the steps after the upload. A bundle publishes whole or not at all, so any one POM in it
# says whether all of it is out. If Central cannot be asked, upload as before: a duplicate is
# refused, never published twice.
if [[ "$type" == AUTOMATIC ]]; then
  pom="$(unzip -Z1 "$bundle" 2> /dev/null | grep -m 1 '\.pom$' || true)"
  if [[ -n "$pom" ]]; then
    version_dir="$(dirname "$pom")"
    artifact_dir="$(dirname "$version_dir")"
    group="$(dirname "$artifact_dir" | tr / .)"
    artifact="$(basename "$artifact_dir")"
    version="$(basename "$version_dir")"
    component="$group:$artifact:$version"
    query="namespace=$(uri "$group")&name=$(uri "$artifact")&version=$(uri "$version")"
    if published="$(central --request GET "$api/published?$query")"; then
      if [[ "$(jq -r '.published' <<< "$published" 2> /dev/null || true)" == true ]]; then
        echo "$component is already published on Central; $bundle is not uploaded again."
        exit 0
      fi
    else
      echo "Could not ask Central whether $component is published ($published); uploading." >&2
    fi
  fi
fi

encoded_name="$(uri "$name")"
if ! response="$(central --request POST --form "bundle=@$bundle" "$api/upload?name=$encoded_name&publishingType=$type")"; then
  echo "Central refused the upload: $response" >&2
  exit 1
fi
id="$(tr -d '[:space:]"' <<< "$response")"
[[ -n "$id" ]] || { echo "Central accepted the upload but returned no deployment id." >&2; exit 1; }
echo "Uploaded $bundle as deployment $id ($type)."

# A status request that fails is asked again: the deployment carries on at Central either way, and
# failing the job over one dropped request would hide a release that went out.
deadline=$((SECONDS + timeout_seconds))
while :; do
  if status="$(central --request POST "$api/status?id=$id")"; then
    state="$(jq -r '.deploymentState // "UNKNOWN"' <<< "$status" 2> /dev/null || echo UNKNOWN)"
  else
    echo "Could not read the status of deployment $id: $status" >&2
    state=UNKNOWN
  fi
  echo "Deployment $id: $state"
  case "$state" in
    PUBLISHING | PUBLISHED)
      if [[ "$type" == AUTOMATIC ]]; then exit 0; fi
      ;;
    VALIDATED)
      if [[ "$type" == USER_MANAGED ]]; then
        central --request DELETE "$api/deployment/$id" > /dev/null
        echo "Deployment $id validated and was dropped; nothing was published."
        exit 0
      fi
      ;;
    FAILED)
      echo "Central refused deployment $id; nothing from it was published:" >&2
      jq '.errors' <<< "$status" >&2
      exit 1
      ;;
  esac
  if [[ "$SECONDS" -ge "$deadline" ]]; then
    echo "Deployment $id is still $state after ${timeout_seconds}s; see https://central.sonatype.com/publishing/deployments" >&2
    exit 1
  fi
  sleep "$poll_seconds"
done
