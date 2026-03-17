#!/bin/bash

MAX_DIR=$(aws s3 ls "s3://${S3_BUCKET}/check_points/${APP_VERSION}/" \
    | awk '{print $2}' \
    | sed 's#/##' \
    | sort -n \
    | tail -1)
aws s3 cp "s3://${S3_BUCKET}/check_points/${APP_VERSION}/${MAX_DIR}/app.tar.gz.enc" / > /dev/null 2>&1
cd /
openssl enc -d -aes-256-cbc -pbkdf2 -pass pass:"$CRAC_CHECKPOINT_SECRET" -in app.tar.gz.enc -out app.tar.gz
tar -xzvf app.tar.gz > /dev/null 2>&1
cd "$APP_HOME" || exit 1
export GLIBC_TUNABLES=glibc.pthread.rseq=0
java \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+IgnoreCPUFeatures \
  -XX:CRaCRestoreFrom=/checkpoint
