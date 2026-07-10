#!/bin/bash

# 上传前端页面到 OSS
# Bucket: sysu-date (cn-hongkong)

echo "开始上传前端页面到 OSS..."

for f in index login register bind forgot-password profile dashboard survey friend_survey modify query admin update; do
    if [ -f "frontend/${f}.html" ]; then
        echo "上传 ${f}.html..."
        aliyun oss cp "frontend/${f}.html" "oss://sysu-date/${f}.html" --region cn-hongkong --force
    else
        echo "跳过 ${f}.html (文件不存在)"
    fi
done

echo "上传完成!"
