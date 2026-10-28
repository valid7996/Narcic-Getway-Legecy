#!/usr/bin/env bash
set -e -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
wanted="$*"
while read -r path url rev; do
  case "$path" in ''|\#*) continue ;; esac
  if [ -n "$wanted" ]; then
    keep=0
    for w in $wanted; do [ "$w" = "$path" ] && keep=1; done
    [ "$keep" = 1 ] || continue
  fi
  dest="$ROOT/$path"
  if [ -d "$dest/.git" ] && [ "$(git -C "$dest" rev-parse HEAD)" = "$rev" ]; then
    echo "== $path already at $rev"
    continue
  fi
  echo "== $path <- $url @ $rev"
  rm -rf "$dest"
  mkdir -p "$(dirname "$dest")"
  auth="$url"
  if [ -n "${GIT_TOKEN:-}" ]; then
    auth="$(printf '%s' "$url" | sed "s#https://#https://x-access-token:${GIT_TOKEN}@#")"
  fi
  cloned=0
  for attempt in 1 2 3; do
    if git clone -q --filter=blob:none "$auth" "$dest"; then
      cloned=1
      break
    fi
    echo "== clone attempt $attempt for $url failed, retrying in 3s..."
    rm -rf "$dest"
    sleep 3
  done
  [ "$cloned" = 1 ] || { echo "::error::failed to clone $url after 3 attempts"; exit 1; }
  git -C "$dest" remote set-url origin "$url"
  git -C "$dest" checkout -q "$rev"
done < "$ROOT/tools/core-sources.txt"
echo "all core sources are at their pinned revisions"
