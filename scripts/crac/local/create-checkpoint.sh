#!/bin/bash

# The URL for the application's health check endpoint.
HEALTH_CHECK_URL="http://localhost:8080/health/check"

POLL_INTERVAL=1 # seconds
MAX_ATTEMPTS=300
ATTEMPTS=0
while [ $ATTEMPTS -lt $MAX_ATTEMPTS ]; do
  HTTP_STATUS=$(curl --silent --output /dev/null --write-out "%{http_code}" "$HEALTH_CHECK_URL")
    if [ "$HTTP_STATUS" -eq 200 ]; then
        echo "✅ Application is healthy (HTTP 200 OK)!"
        docker exec -it demo jcmd Application JDK.checkpoint
        break
    else
        echo "   ... still waiting (Status: $HTTP_STATUS). Retrying in $POLL_INTERVAL second(s)..."
        sleep $POLL_INTERVAL
        ATTEMPTS=$((ATTEMPTS + 1))
    fi
done

