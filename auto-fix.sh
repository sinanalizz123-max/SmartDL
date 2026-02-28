#!/data/data/com.termux/files/usr/bin/bash

MAX_TRIES=5
COUNT=0

while [ $COUNT -lt $MAX_TRIES ]; do
  echo "=============================="
  echo "🚀 Attempt $COUNT"
  echo "=============================="

  git add .
  git commit -m "auto-fix attempt $COUNT" || echo "Nothing to commit"
  git push origin main

  echo "⏳ Waiting for workflow to start..."
  sleep 15

  RUN_ID=$(gh run list --limit 1 --json databaseId --jq '.[0].databaseId')

  if [ -z "$RUN_ID" ]; then
    echo "❌ Could not detect workflow run."
    exit 1
  fi

  echo "🔍 Monitoring run $RUN_ID"

  # Wait while build is in progress
  while true; do
    STATUS=$(gh run view $RUN_ID --json status --jq '.status')

    if [ "$STATUS" = "in_progress" ] || [ "$STATUS" = "queued" ]; then
      echo "⏳ Build still running... waiting 10 seconds"
      sleep 10
    else
      break
    fi
  done

  CONCLUSION=$(gh run view $RUN_ID --json conclusion --jq '.conclusion')

  if [ "$CONCLUSION" = "success" ]; then
    echo "✅ Build succeeded!"
    exit 0
  fi

  echo "❌ Build failed. Fetching logs..."
  gh run view $RUN_ID --log > ci-error.log

  echo "🤖 Sending logs to Codex..."

  CODEX_APPROVAL_MODE=none codex exec "
  Read README_AI.md fully.
  Understand architecture and constraints.
  Read ci-error.log carefully.
  Identify only build or Gradle errors.
  Fix only necessary files.
  Do not refactor unrelated logic.
  Preserve architecture and storage rules.
  "

  COUNT=$((COUNT+1))
done

echo "❌ Max retries reached. Manual review required."
exit 1
