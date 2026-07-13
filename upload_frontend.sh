#!/bin/sh
set -eu

# Upload the public frontend pages to the original OSS bucket.
# Requires the Aliyun CLI to be configured by the operator.
for page in \
  admin bind dashboard forgot-password friend_survey index login modify \
  profile query register survey terms update
do
  aliyun oss cp "frontend/${page}.html" \
    "oss://sysu-date/${page}.html" \
    --region cn-hongkong \
    --force
done
