#!/data/data/com.termux/files/usr/bin/bash

ATTEMPT=0
CHECKPOINT_INTERVAL=5

cd /data/data/com.termux/files/home/SmartDL || exit 1

while true; do
  echo "=============================="
  echo "🚀 Attempt $ATTEMPT"
  echo "=============================="

  git add -A
  git reset ci-error.log .write_test_tmp 2>/dev/null
  git commit -m "auto-fix attempt $ATTEMPT" || echo "Nothing to commit"
  git push origin main

  echo "⏳ Waiting for workflow to start..."
  sleep 15

  RUN_ID=$(gh run list --limit 1 --json databaseId --jq '.[0].databaseId')

  if [ -z "$RUN_ID" ]; then
    echo "❌ No workflow detected. Waiting 20s..."
    sleep 20
    continue
  fi

  echo "🔍 Monitoring run $RUN_ID"

  while true; do
    STATUS=$(gh run view $RUN_ID --json status --jq '.status')

    if [ "$STATUS" = "in_progress" ] || [ "$STATUS" = "queued" ]; then
      echo "⏳ Build running... waiting 10s"
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
  Fix only build or Gradle errors.
  Do not refactor unrelated files.
  Preserve architecture.
  "

  ATTEMPT=$((ATTEMPT+1))

  # 🔥 Checkpoint pause every 5 attempts
  if (( ATTEMPT % CHECKPOINT_INTERVAL == 0 )); then
    echo "⚠️  $ATTEMPT attempts reached."
    echo "Pausing for 60 seconds..."
    sleep 60

    echo "Continue automation? (y/n) [default: y in 20s]"
    read -t 20 RESPONSE

    if [ "$RESPONSE" = "n" ]; then
      echo "🛑 Automation stopped by user."
      exit 0
    else
      echo "▶ Continuing..."
    fi
  fi
done
