#!/bin/bash

# Using manual PID tuning to
# https://docs.azul.com/core/crac/crac-debugging#manual-pid-tuning
( echo 128 > /proc/sys/kernel/ns_last_pid ) 2>/dev/null || while [ "$(cat /proc/sys/kernel/ns_last_pid)" -lt 128 ]; do :; done;

# The URL for the application's health check endpoint.
HEALTH_CHECK_URL="http://localhost:8080/health/check"

CRAC_CHECKPOINT_DIR=/checkpoint

# --- Script Logic ---

# 1. Validate prerequisites
if ! command -v java &> /dev/null; then
    echo "❌ Error: 'java' command not found. Please ensure a CRaC-enabled JDK is installed and in your PATH."
    exit 1
fi

if ! command -v jcmd &> /dev/null; then
    echo "❌ Error: 'jcmd' command not found. Please ensure your JDK's bin directory is in your PATH."
    exit 1
fi

if ! command -v curl &> /dev/null; then
    apk add curl
fi

if ! command -v aws &> /dev/null; then
    apk add aws-cli
fi

# 2. Start the Spring Boot application in the background
echo "🚀 Starting Spring Boot application"

export GLIBC_TUNABLES=glibc.pthread.rseq=0

# Define the list
objects=(
  "appName:app command:io.bimurto.crac.Application"
)

# Loop through the list
for obj in "${objects[@]}"
do
  # Extract appName and command
  appName=$(echo "$obj" | awk -F 'appName:' '{print $2}' | awk '{print $1}')
  command=$(echo "$obj" | awk -F 'command:' '{print $2}')

  cd "$APP_HOME" || exit 1

  echo "App Name: $appName"
  echo "Command: $command"
  echo "----"

  rm -rf "$CRAC_CHECKPOINT_DIR"
  mkdir -p "$CRAC_CHECKPOINT_DIR"

  # Start the application in the background
  # cpu features are set to -XX:CPUFeatures=generic, as this would provide better compatibility
  java \
    -Dspring.profiles.include="crac" \
    -XX:+UnlockExperimentalVMOptions \
    -XX:CPUFeatures=generic \
    -XX:+IgnoreCPUFeatures \
    -XX:CRaCCheckpointTo="$CRAC_CHECKPOINT_DIR" \
    -cp "$APP_HOME":"$APP_HOME"/lib/* \
    "$command" > /dev/stdout 2>&1 &

  # Capture the Process ID (PID) of the background application
  APP_PID=$!
  echo "   Application started with PID: $APP_PID"

  # 3. Poll the health endpoint
  echo "🩺 Waiting for application to become healthy at $HEALTH_CHECK_URL..."
  POLL_INTERVAL=1 # seconds
  MAX_ATTEMPTS=300
  ATTEMPTS=0

  while [ $ATTEMPTS -lt $MAX_ATTEMPTS ]; do
      # Use curl to get the HTTP status code
      HTTP_STATUS=$(curl --silent --output /dev/null --write-out "%{http_code}" "$HEALTH_CHECK_URL")

      if [ "$HTTP_STATUS" -eq 200 ]; then
          echo "✅ Application is healthy (HTTP 200 OK)!"
          break
      else
          echo "   ... still waiting (Status: $HTTP_STATUS). Retrying in $POLL_INTERVAL second(s)..."
          sleep $POLL_INTERVAL
          ATTEMPTS=$((ATTEMPTS + 1))
      fi

      # Check if the background process is still running
      if ! kill -0 $APP_PID 2>/dev/null; then
          echo "❌ Error: Application with PID $APP_PID terminated unexpectedly."
          exit 1
      fi
  done

  if [ $ATTEMPTS -eq $MAX_ATTEMPTS ]; then
      echo "❌ Error: Timed out waiting for application to become healthy after $MAX_ATTEMPTS attempts."
      echo "   Stopping application."
      kill $APP_PID
      exit 1
  fi

  # 4. Create the checkpoint
  echo "📸 Creating CRaC checkpoint for PID: $APP_PID..."
  if ! jcmd "$APP_PID" JDK.checkpoint; then
      echo "❌ Error: 'jcmd' failed to create the checkpoint for PID $APP_PID."
      echo "   The application might still be running. Attempting to stop it..."
      kill "$APP_PID"
      exit 1
  fi

  # The jcmd command will stop the process. We wait a moment to ensure it has fully terminated.
  sleep 2

  if kill -0 $APP_PID 2>/dev/null; then
      echo "⚠️ Warning: Application with PID $APP_PID did not stop as expected. Forcing shutdown."
      kill -9 "$APP_PID"
      exit 1
  else
      echo "🛑 Application stopped successfully after checkpoint."
  fi

  echo " Checkpoint successfully created in: $CRAC_CHECKPOINT_DIR"

  echo "Compressing checkpoints to a tarball"
  tar -czf "$appName".tar.gz "$CRAC_CHECKPOINT_DIR/"
  openssl enc -aes-256-cbc -salt -pbkdf2 -pass pass:"$CRAC_CHECKPOINT_SECRET" -in "$appName".tar.gz -out "$appName".tar.gz.enc

  echo "Done."
done

# Get highest-numbered "directory"
CURRENT_MAX_DIR=$(aws s3 ls "s3://${S3_BUCKET}/check_points/${APP_VERSION}/" \
    | awk '{print $2}' \
    | sed 's#/##' \
    | sort -n \
    | tail -1)

# Default to 1 if none found
if [[ -z "$CURRENT_MAX_DIR" ]]; then
    DIR_NUM=1
else
    DIR_NUM=$((CURRENT_MAX_DIR + 1))
fi

# Loop through the list
for obj in "${objects[@]}"
do
  # Extract appName and command
  appName=$(echo "$obj" | awk -F 'appName:' '{print $2}' | awk '{print $1}')

  echo "Copying $appName files to S3 bucket"
  S3_PATH="s3://${S3_BUCKET}/check_points/${APP_VERSION}/${DIR_NUM}/$appName.tar.gz.enc"
  aws s3 cp "$appName".tar.gz.enc "${S3_PATH}"
  echo "$appName copy done."

done

exit 0
