#!/usr/bin/env bash
# 운영과 같은 조건(prod 프로필, 실제 MySQL, 실제 마이그레이션, 만들어진 jar)으로 앱을 띄워 보고,
# /api/public/health/db가 200을 줄 때까지 기다린다. 테스트(H2, 설정 파일 덮어쓰기)에서는 만들어지지 않는 빈이나
# MySQL에서만 터지는 마이그레이션이 운영에서 처음 드러나 서버가 죽는 일(2026-10-23 장애)을 배포 전에 잡는다.
#
# 사용: DB_URL=jdbc:mysql://localhost:3306/chak DB_USERNAME=root DB_PASSWORD=... scripts/prod-boot-check.sh [jar]
set -uo pipefail

JAR="${1:-$(ls build/libs/*.jar | grep -v plain | head -1)}"
PORT="${CHECK_PORT:-18080}"
LOG="$(mktemp)"
WAIT_SECONDS="${CHECK_WAIT_SECONDS:-120}"

: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:=}"

# AWS에 붙지 않게 한다(자격 증명이 없으면 시크릿 읽기는 경고만 남기고 환경변수 값으로 계속한다).
export AWS_EC2_METADATA_DISABLED=true
export AWS_REGION=ap-southeast-2

SPRING_PROFILES_ACTIVE=prod \
SERVER_PORT="$PORT" \
JWT_SECRET="ci-boot-check-secret-$(date +%s)-0123456789abcdef" \
CHAK_SECRETS_ID="" \
DB_SECRET_ID="rds!db-ci-boot-check" \
CORS_ALLOWED_ORIGINS="https://www.chak.live" \
S3_BUCKET="ci-boot-check-bucket" \
java -Xmx512m -XX:+ExitOnOutOfMemoryError -jar "$JAR" > "$LOG" 2>&1 &
APP_PID=$!
trap 'kill "$APP_PID" 2>/dev/null || true; wait "$APP_PID" 2>/dev/null || true' EXIT

for _ in $(seq 1 "$WAIT_SECONDS"); do
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "::error::앱이 시작 도중 종료됐습니다. 마지막 로그:"
    tail -60 "$LOG"
    exit 1
  fi
  STATUS="$(curl -s -o /tmp/chak-boot-health.json -w '%{http_code}' "http://localhost:$PORT/api/public/health/db" || true)"
  if [ "$STATUS" = "200" ]; then
    echo "OK: 운영 프로필로 기동했고 DB 헬스체크가 200입니다. $(cat /tmp/chak-boot-health.json)"
    exit 0
  fi
  sleep 1
done

echo "::error::${WAIT_SECONDS}초 안에 /api/public/health/db가 200을 주지 않았습니다(마지막 응답: ${STATUS:-없음}). 마지막 로그:"
tail -60 "$LOG"
exit 1
