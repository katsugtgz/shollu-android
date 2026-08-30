#!/usr/bin/env bash
# Watch PR #21 (katsugtgz/shollu-android) for new issue/review comments and state changes.
seen=5466470604
while true; do
  for kind in issues pulls; do
    out=$(gh api "repos/katsugtgz/shollu-android/${kind}/21/comments" --jq '.[] | select(.id > '"$seen"') | [.id, .user.login, (.body | gsub("\n";" ") | .[0:160])] | @tsv' 2>/dev/null) || true
    if [ -n "$out" ]; then
      printf '%s\n' "$out" | while IFS=$'\t' read -r id author body; do
        echo "PR#21 new ${kind} comment ${id} by ${author}: ${body}"
      done
      maxid=$(printf '%s\n' "$out" | awk -F'\t' 'NR==1{m=$1} {if($1>m)m=$1} END{print m}')
      seen=$maxid
    fi
  done
  state=$(gh pr view 21 --repo katsugtgz/shollu-android --json state --jq .state 2>/dev/null) || true
  if [ "$state" != "OPEN" ]; then echo "PR#21 state changed: ${state}"; exit 0; fi
  sleep 90
done
