#!/bin/bash
# AIRA — Automated Investigation and Response Algorithm
# Run script with all required configurations

# ============================================================
# CONFIGURATION — Edit these values
# ============================================================

# Jira / Confluence (same Atlassian token works for both)
JIRA_TOKEN="${JIRA_API_TOKEN:-}"

if [ -z "$JIRA_TOKEN" ]; then
    echo "ERROR: JIRA_API_TOKEN environment variable is not set."
    echo "Set it with: export JIRA_API_TOKEN=your_token_here"
    exit 1
fi

# Source code repos (comma-separated local paths)
# Example: /Users/faizfarhan/projects/creditfile/WEB-INF/src,/Users/faizfarhan/projects/another-app/src
SOURCE_REPOS=""

# Log directory (local folder containing application logs)
# Example: /var/log/apps or /Users/faizfarhan/logs
LOGS_PATH=""

# ============================================================
# JVM ARGUMENTS (SSL truststore for corporate Jira/Confluence)
# ============================================================
JVM_ARGS="-Djavax.net.ssl.trustStore=./truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"

# ============================================================
# APPLICATION ARGUMENTS
# ============================================================
APP_ARGS=""
APP_ARGS="$APP_ARGS --jira.api-token=$JIRA_TOKEN"
APP_ARGS="$APP_ARGS --confluence.api-token=$JIRA_TOKEN"

if [ -n "$SOURCE_REPOS" ]; then
    APP_ARGS="$APP_ARGS --sourcecode.repo-paths=$SOURCE_REPOS"
fi

if [ -n "$LOGS_PATH" ]; then
    APP_ARGS="$APP_ARGS --logs.base-path=$LOGS_PATH"
fi

# ============================================================
# RUN
# ============================================================
echo "=================================================="
echo "  AIRA — Automated Investigation and Response"
echo "=================================================="
echo "  Jira:       https://ctosrepo.atlassian.net"
echo "  Confluence: https://ctosrepo.atlassian.net/wiki"
echo "  Source:     ${SOURCE_REPOS:-not configured}"
echo "  Logs:       ${LOGS_PATH:-not configured}"
echo "  UI:         http://localhost:8080"
echo "=================================================="
echo ""

mvn spring-boot:run \
    -Dspring-boot.run.jvmArguments="$JVM_ARGS" \
    -Dspring-boot.run.arguments="$APP_ARGS"
