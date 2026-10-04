#!/usr/bin/env bash
# Announces a new alpha build on Telegram (optional: needs TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID).
set -uo pipefail

: "${COMMIT_LOG:=● No new commits since the last build}"
: "${VERSION:?VERSION env var required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY env var required}"
: "${SKIP_BUILD:=false}"
: "${APK_PATH:=}"
: "${CHECKSUM:=}"
: "${TELEGRAM_BOT_TOKEN:=}"
: "${TELEGRAM_CHAT_ID:=}"
: "${TELEGRAM_THREAD_ID:=}"

if [ "$SKIP_BUILD" = "true" ] || [ -z "$TELEGRAM_BOT_TOKEN" ] || [ -z "$TELEGRAM_CHAT_ID" ] || [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
  echo "Skipping Telegram upload (SKIP_BUILD=$SKIP_BUILD or missing bot token/chat id/APK)"
  exit 0
fi

api_get() {
  curl -fsS "$@" || echo '[]'
}

fetch_user_details() {
  local login=$1
  local user_details
  user_details=$(api_get "https://api.github.com/users/$login")
  local name
  name=$(echo "$user_details" | jq -r '.name // .login // empty')
  [ -z "$name" ] && name="$login"
  echo "$name|$login"
}

fetch_all_contributors() {
  local page=1
  local all="[]"
  while :; do
    local batch
    batch=$(api_get -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28" "https://api.github.com/repos/${GITHUB_REPOSITORY}/contributors?per_page=100&page=${page}")
    local count
    count=$(echo "$batch" | jq 'length' 2>/dev/null || echo 0)
    [ "$count" -eq 0 ] && break
    all=$(jq -c -n --argjson a "$all" --argjson b "$batch" '$a + $b')
    [ "$count" -lt 100 ] && break
    page=$((page + 1))
  done
  echo "$all"
}

declare -A additional_info
additional_info["Arkhins-0"]="\n AniList: [Arkhins](<https://anilist.co/user/5998467/>)"

declare -A recent_commit_counts
while read -r count name; do
  [ -z "$name" ] && continue
  recent_commit_counts["$name"]=$count
done < <(echo "$COMMIT_LOG" | sed 's/%0A/\n/g' | grep -oP '(?<=~)[^[]*' | sort | uniq -c | sort -rn)

echo "Fetching contributors from GitHub"
contributors=$(fetch_all_contributors)

sorted_contributors=$(for login in $(echo "$contributors" | jq -r '.[].login'); do
  user_info=$(fetch_user_details "$login")
  name=$(echo "$user_info" | cut -d'|' -f1)
  count=${recent_commit_counts["$name"]:-0}
  echo "$count|$login"
done | sort -rn | cut -d'|' -f2)

developers=""
while read -r login; do
  [ -z "$login" ] && continue
  user_info=$(fetch_user_details "$login")
  name=$(echo "$user_info" | cut -d'|' -f1)
  login=$(echo "$user_info" | cut -d'|' -f2)

  commit_count=${recent_commit_counts["$name"]:-0}
  [ "$commit_count" -gt 0 ] || continue

  branch_commit_count=$(git log --author="$login" --author="$name" --oneline | awk '!seen[$0]++' | wc -l)
  extra_info="${additional_info[$name]:-}"
  if [ -n "$extra_info" ]; then
    extra_info=$(echo "$extra_info" | sed 's/\\n/\n- /g')
  fi

  developer_entry="◗ **${name}** ${extra_info}
- Github: [${login}](https://github.com/${login})
- Commits: ${branch_commit_count}"
  if [ -n "$developers" ]; then
    developers="${developers}
${developer_entry}"
  else
    developers="${developer_entry}"
  fi
done <<< "$sorted_contributors"

max_length=1000
if [ ${#developers} -gt $max_length ]; then
  developers="${developers:0:$max_length}... (truncated)"
fi

telegram_commit_messages=$(echo "$COMMIT_LOG" | sed 's/%0A/\n/g' | while read -r line; do
  message=$(echo "$line" | sed -E 's/● (.*) ~(.*) \[֍\]\((.*)\)/● \1 ~\2 <a href="\3">֍<\/a>/')
  message=$(echo "$message" | sed -E 's/\[#([0-9]+)\]\((https:\/\/github\.com\/[^)]+)\)/<a href="\2">#\1<\/a>/g')
  echo "$message"
done)
telegram_commit_messages="<blockquote>${telegram_commit_messages}</blockquote>"

echo "$developers" > dev_info.txt
chmod +x workflowscripts/tel_parser.sed
./workflowscripts/tel_parser.sed dev_info.txt >> output.txt
dev_info_tel=$(< output.txt)
telegram_dev_info="<blockquote>${dev_info_tel}</blockquote>"

caption="New Alpha-Build dropped 🔥

Commits:
${telegram_commit_messages}
Dev:
${telegram_dev_info}
version: ${VERSION}"
if [ -n "$CHECKSUM" ]; then
  caption="${caption}
SHA256: ${CHECKSUM}"
fi

curl -fsS -X POST \
  "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendDocument" \
  -F "chat_id=${TELEGRAM_CHAT_ID}" \
  ${TELEGRAM_THREAD_ID:+-F "message_thread_id=${TELEGRAM_THREAD_ID}"} \
  -F "document=@${APK_PATH}" \
  -F "caption=${caption}" \
  -F "parse_mode=HTML" >/dev/null \
  && echo "APK uploaded to Telegram" \
  || echo "::warning::APK upload to Telegram failed"
