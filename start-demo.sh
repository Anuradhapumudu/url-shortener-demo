#!/usr/bin/env bash
# Starts the shortener and makes it public at https://go.anuradha.indevs.in (via Cloudflare Tunnel).
# Ctrl+C stops both.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
	echo "Missing .env - copy .env.example to .env and fill in the database details."
	exit 1
fi

set -a
. ./.env
set +a

if [ -z "${SHORT_LINK_API_KEY:-}" ]; then
	echo "Error: SHORT_LINK_API_KEY must be set in .env before running public tunnel"
	exit 1
fi

if [ -z "${SHORT_LINK_ALLOWED_HOSTS:-}" ]; then
	echo "Error: SHORT_LINK_ALLOWED_HOSTS must be set in .env before exposing public tunnel"
	exit 1
fi

if [ -z "${SHORT_LINK_TENANT_ID:-}" ]; then
	echo "Error: SHORT_LINK_TENANT_ID must be set in .env to isolate this domain's links"
	exit 1
fi

if lsof -iTCP:8090 -sTCP:LISTEN >/dev/null 2>&1; then
	echo "Port 8090 is already in use - the demo is probably already running."
	echo "Stop it first: pkill -f url-shortener-demo; pkill -f 'cloudflared tunnel run'"
	exit 1
fi

echo "Building..."
mvn -q -DskipTests package

SPRING_PROFILES_ACTIVE=prod SHORT_LINK_DEMO_UI_ENABLED=true \
	java -jar target/url-shortener-demo-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
APP_PID=$!
trap 'kill $APP_PID 2>/dev/null' EXIT

echo "Starting app (log: app.log)..."
until curl -s -o /dev/null http://localhost:8090/actuator/health; do
	if ! kill -0 $APP_PID 2>/dev/null; then
		echo "App failed to start. Last lines of app.log:"
		tail -20 app.log
		exit 1
	fi
	sleep 2
done

echo ""
echo "  Local:       http://localhost:8090"
echo "  Public:      https://go.anuradha.indevs.in"
echo "  Open the public URL and enter the API key in the UI when prompted."
echo ""
cloudflared tunnel run --url http://localhost:8090 url-shortener-demo
