#!/bin/bash
# Babysit watch for PR #23: cubic review runs, comment/review counts, CI checks.
prev=""
while true; do
  cur=$(gh pr view 23 --json comments,reviews,statusCheckRollup,mergeable --jq '"runs=\([.comments[].body] | join("\n") | scan("cubic:review-run=[0-9a-f-]+") | unique | length) inline=\(.comments | length) reviews=\(.reviews | length) mergeable=\(.mergeable) checks=\([.statusCheckRollup[]? | "\(.name):\(.conclusion // .status)"] | sort | join(","))"' 2>/dev/null)
  if [ -n "$prev" ] && [ -n "$cur" ] && [ "$cur" != "$prev" ]; then echo "PR#23 CHANGE: $cur"; fi
  prev="$cur"
  sleep 50
done
