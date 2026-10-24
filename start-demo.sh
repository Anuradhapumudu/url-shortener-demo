#!/usr/bin/env bash
# Starts the shortener and makes it public at https://go.pumudu.indevs.in (via Cloudflare Tunnel).
# Ctrl+C stops both.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
	echo "Missing .env - copy .env.example to .env and fill in the database details."
	exit 1
fi

if lsof -iTCP:8090 -sTCP:LISTEN >/dev/null 2>&1; then
	echo "Port 8090 is already in use - the demo is probably already running."
	echo "Stop it first: pkill -f url-shortener-demo; pkill -f 'cloudflared tunnel run'"
	exit 1
fi

echo "Building..."
mvn -q -DskipTests package

java -jar target/url-shortener-demo-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
APP_PID=$!
trap 'kill $APP_PID 2>/dev/null' EXIT

echo "Starting app (log: app.log)..."
until curl -s -o /dev/null http://localhost:8090/api/links/summary; do
	if ! kill -0 $APP_PID 2>/dev/null; then
		echo "App failed to start. Last lines of app.log:"
		tail -20 app.log
		exit 1
	fi
	sleep 2
done

echo ""
echo "  Local:  http://localhost:8090"
echo "  Public: https://go.pumudu.indevs.in"
echo ""
cloudflared tunnel run --url http://localhost:8090 url-shortener-demo
