#!/usr/bin/env bash
# 배포 구성 스모크 — 기본 설정으로 띄운 배포 구성(deploy/compose.yaml)이 갖춰야 할 것을 확인한다.
# CI(.github/workflows/ci.yml 의 deploy job)와 로컬이 같은 파일을 쓴다. 필요한 호스트 도구: bash · docker · curl.
# 사용: 배포 구성을 띄운 뒤(PROXY_HOST_ADDRESS 는 기본값) bash deploy/smoke.sh — 종료 코드 0 이면 전 항목 통과다.
set -uo pipefail

deploy_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(dirname "$deploy_dir")
compose=(docker compose -f "$deploy_dir/compose.yaml")
body_file=$(mktemp)
trap 'rm -f "$body_file"' EXIT
failures=0

pass() { printf 'ok    %s\n' "$1"; }
fail() {
  printf 'FAIL  %s — 기대: %s / 실제: %s\n' "$1" "$2" "$3"
  failures=$((failures + 1))
}

# 서비스마다 "상태 [공개 주소 …]" — 공개하지 않은 포트는 나오지 않는다
services=$("${compose[@]}" ps --format '{{.Service}} {{.Health}} [{{range .Publishers}}{{if .PublishedPort}}{{.URL}}:{{.PublishedPort}} {{end}}{{end}}]')
service_line() { grep "^$1 " <<<"$services" | head -n 1; }

# K1 세 서비스가 healthy
for service in postgres app proxy; do
  line=$(service_line "$service")
  if [[ $line == "$service healthy "* ]]; then pass "K1 $service healthy"; else fail "K1 $service healthy" "$service healthy" "${line:-없음}"; fi
done

# K2 proxy 만 공개하고, 기본 바인딩은 루프백
for service in postgres app; do
  line=$(service_line "$service")
  if [[ $line == *" []" ]]; then pass "K2 $service 공개 포트 없음"; else fail "K2 $service 공개 포트 없음" "[]" "${line:-없음}"; fi
done
line=$(service_line proxy)
if [[ $line =~ \[127\.0\.0\.1:[0-9]+\ \]$ ]]; then pass "K2 proxy 는 127.0.0.1 에만 공개"; else fail "K2 proxy 는 127.0.0.1 에만 공개" "[127.0.0.1:<포트> ]" "${line:-없음}"; fi

address=$("${compose[@]}" port proxy 8080) || address=""
if [[ -z $address ]]; then
  fail "proxy 주소" "127.0.0.1:<포트>" "없음"
  printf '%d 항목 실패\n' "$failures"
  exit 1
fi
base="http://$address"

# 경로에 GET 을 보내 "상태 형식" 을 돌려주고 본문은 body_file 에 둔다
request() {
  curl -sS --max-time 10 -o "$body_file" -w '%{http_code} %{content_type}' "$base$1" 2>&1 || true
}

# 이름 경로 기대-상태(* 는 아무 값) 기대-형식-접두
check() {
  local name=$1 path=$2 status=$3 type=$4 actual
  actual=$(request "$path")
  if [[ ($status == "*" || ${actual%% *} == "$status") && ${actual#* } == "$type"* ]]; then
    pass "$name"
  else
    fail "$name" "$status $type… ($path)" "$actual"
  fi
}

# K3 SPA 첫 화면
check "K3 SPA 첫 화면" / 200 text/html
if grep -qF '<div id="root"></div>' "$body_file"; then pass "K3 SPA 루트 요소"; else fail "K3 SPA 루트 요소" '<div id="root"></div>' "$(head -c 200 "$body_file")"; fi
# K4 SPA 딥 링크 — 없는 파일 경로도 index.html 로 응답한다
check "K4 SPA 딥 링크" /smoke/deep/link 200 text/html
# K5 화면용 API 는 앱으로 넘긴다
check "K5 /api/ping 을 앱으로" /api/ping 200 application/json
body=$(cat "$body_file")
if [[ $body == '{"message":"pong"}' ]]; then pass "K5 /api/ping 응답"; else fail "K5 /api/ping 응답" '{"message":"pong"}' "$body"; fi
# K6 로그인 경로는 앱으로 넘긴다(앱이 JSON 오류로 답한다)
check "K6 /login/oauth2/… 를 앱으로" /login/oauth2/code/smoke "*" application/json
# K7 · K8 관리 · 명세 엔드포인트는 넘기지 않는다(SPA 가 답한다)
check "K7 /actuator/health 는 넘기지 않음" /actuator/health 200 text/html
check "K8 /v3/api-docs 는 넘기지 않음" /v3/api-docs 200 text/html
# K10 넘기는 경로의 경계 — /login/* 만 넘기고 /login 자체는 SPA
check "K10 /login 은 넘기지 않음" /login 200 text/html

# K9 배포 postgres 는 개발용 정의의 이미지를 그대로 쓴다(정본: 저장소 루트 compose.yaml)
# 두 명령이 모두 성공해야 비교한다 — 같은 오류 메시지끼리 같다고 통과하지 않는다
deploy_image=$("${compose[@]}" config --images postgres 2>&1) && deploy_ok=1 || deploy_ok=0
dev_image=$(docker compose -f "$repo_dir/compose.yaml" --profile dev config --images postgres 2>&1) && dev_ok=1 || dev_ok=0
if ((deploy_ok && dev_ok)) && [[ -n $dev_image && $deploy_image == "$dev_image" ]]; then pass "K9 PostgreSQL 이미지 ($deploy_image)"; else fail "K9 PostgreSQL 이미지" "$dev_image" "$deploy_image"; fi

# K11 app 은 두 역할(화면 · 작업)을 한 프로세스에서 한다 — 켜진 profile 은 Spring Boot 기동 로그의 한 줄로 본다
roles_expected='The following 2 profiles are active: "web", "worker"'
roles_line=$("${compose[@]}" logs --no-log-prefix app 2>&1 | grep -m 1 -E 'profiles? (is|are) active|No active profile set')
if [[ $roles_line == *"$roles_expected" ]]; then pass "K11 app 의 역할 web · worker"; else fail "K11 app 의 역할 web · worker" "$roles_expected" "${roles_line:-없음}"; fi

# K12 배포 이미지가 저장소의 프리셋을 모두 적재한다 — 앱의 적재 로그 줄(PresetCatalogConfiguration)을 저장소 파일 수와 비교한다
preset_files=("$repo_dir"/backend/src/main/resources/source-presets/*.yaml)
[[ -e ${preset_files[0]} ]] || preset_files=()
loaded=$("${compose[@]}" logs --no-color app 2>&1 | grep -o '프리셋 [0-9]*개를 적재했다' | tail -n 1)
if [[ $loaded == "프리셋 ${#preset_files[@]}개를 적재했다" ]]; then pass "K12 프리셋 적재 (${#preset_files[@]}개)"; else fail "K12 프리셋 적재" "프리셋 ${#preset_files[@]}개를 적재했다" "${loaded:-적재 로그 없음}"; fi

if ((failures > 0)); then
  printf '%d 항목 실패\n' "$failures"
  exit 1
fi
printf '전 항목 통과\n'
