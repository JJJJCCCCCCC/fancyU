#!/bin/sh
set -eu

# Upload the public frontend pages to the original OSS bucket.
# Requires the Aliyun CLI and an HTTPS API endpoint configured by the operator.
: "${API_BASE_URL:?Set API_BASE_URL to the public HTTPS Java API endpoint}"
if ! printf '%s' "$API_BASE_URL" |
  grep -Eq '^https://[A-Za-z0-9._~:/?#@!$&()*+,;=%-]+$'
then
  echo "API_BASE_URL must be a valid HTTPS URL" >&2
  exit 1
fi

config_file=$(mktemp)
trap 'rm -f "$config_file"' EXIT
printf 'window.SYSUDATE_CONFIG = Object.freeze({ apiBase: "%s" });\n' \
  "$API_BASE_URL" > "$config_file"

for page in \
  admin bind dashboard forgot-password friend_survey index login modify \
  profile query register survey terms update
do
  aliyun oss cp "frontend/${page}.html" \
    "oss://sysu-date/${page}.html" \
    --region cn-hongkong \
    --force
done

aliyun oss cp "$config_file" \
  "oss://sysu-date/config.js" \
  --region cn-hongkong \
  --force
