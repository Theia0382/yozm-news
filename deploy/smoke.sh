#!/usr/bin/env bash
# 배포 구성 스모크 — 띄운 배포 구성(deploy/compose.yaml)이 갖춰야 할 것을 확인한다. 공개 주소는 기본값이어야 하고, 운영자 토큰은 넣어도 넣지 않아도 된다.
# CI(.github/workflows/ci.yml 의 deploy-amd64 · deploy-arm64 job)와 로컬이 같은 파일을 쓴다. 필요한 호스트 도구: bash · docker · curl · sed.
# 사용: 배포 구성을 띄운 뒤(PROXY_HOST_ADDRESS 는 기본값) bash deploy/smoke.sh — 종료 코드 0 이면 전 항목 통과다.
# K14(로컬 모델 추론 한 건)는 스모크 덧씌우기(deploy/smoke/compose.yaml)의 시험 피드 서버 feeds 가 떠 있고 운영자 토큰이 있을 때만 돈다. 덧씌우기 경로를 SMOKE_OVERLAY 로 주면 스모크의 compose 명령에도 얹는다.
# 아니면 건너뛴다 — 운영 인스턴스에서 돌려도 시험 소스를 등록하지 않는다. SMOKE_REQUIRE_K14=1 이면 건너뛰지 않고 실패한다(CI).
set -uo pipefail

deploy_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(dirname "$deploy_dir")
compose=(docker compose -f "$deploy_dir/compose.yaml")
if [[ -n ${SMOKE_OVERLAY:-} ]]; then compose+=(-f "$SMOKE_OVERLAY"); fi
body_file=$(mktemp)
trap 'rm -f "$body_file"' EXIT
failures=0

pass() { printf 'ok    %s\n' "$1"; }
fail() {
  printf 'FAIL  %s — 기대: %s / 실제: %s\n' "$1" "$2" "$3"
  failures=$((failures + 1))
}
skip() { printf 'skip  %s — %s\n' "$1" "$2"; }

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
# K6 로그인 경로는 앱으로 넘긴다(앱이 문제 응답으로 답한다 — docs/specs/platform.md "오류 응답 형식")
check "K6 /login/oauth2/… 를 앱으로" /login/oauth2/code/smoke "*" application/problem+json
# K13 운영자 경로는 토큰 없는 요청에 열리지 않는다 — 토큰이 없는 배포는 닫혀 있고, 토큰을 넣은 배포는 인증을 요구한다(docs/specs/platform.md "운영자 접근 통제")
# 토큰 유무는 실행 중인 app 의 환경을 컨테이너 안의 bash 가 보고 표지(set · unset)만 내보낸다 — 토큰 값은 이 스크립트로 나오지 않는다
# 종료 코드 0 과 표지가 함께 있어야 판정한 것이다 — app 이 떠 있지 않아도 exec 가 실패하므로, 읽지 못한 것을 토큰 없음으로 넘기지 않는다
token_marker=$("${compose[@]}" exec -T app bash -c '[[ -n ${YOZMNEWS_ADMIN_TOKEN:-} ]] && echo set || echo unset' 2>/dev/null) && token_read=1 || token_read=0
k13_name=""
case "$token_read:$token_marker" in
  1:unset) k13_name="K13 운영자 경로 닫힘(토큰 없음)" k13_status=403 k13_problem=urn:yozmnews:problem:admin-closed ;;
  1:set) k13_name="K13 운영자 경로 인증 요구(토큰 있음)" k13_status=401 k13_problem=urn:yozmnews:problem:admin-unauthorized ;;
esac
if [[ -z $k13_name ]]; then
  fail "K13 운영자 토큰 설정" "app 환경의 표지 set · unset" "운영자 토큰 설정을 읽지 못했다"
else
  check "$k13_name" /api/admin/sources "$k13_status" application/problem+json
  if grep -qF "\"$k13_problem\"" "$body_file"; then pass "$k13_name 문제 유형"; else fail "$k13_name 문제 유형" "$k13_problem" "$(head -c 200 "$body_file")"; fi
fi
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

# K14 이미지의 로컬 임베딩 모델이 이 아키텍처에서 추론한다 — 시험 피드의 기사 하나가 수집 · 표현까지 가고, 표현의 모델 버전이 저장소의 정본과 같다
# 시험 소스를 운영자 API 로 등록 · 켠다. 요청은 app 컨테이너 안의 bash 가 컨테이너의 토큰으로 보낸다(이미지에 curl 이 없다 — healthcheck 와 같은 /dev/tcp) — 토큰 값은 이 스크립트로 나오지 않는다
# 본문은 표준 입력으로 넘기고, 응답은 상태 코드 한 줄 다음 본문이다
admin_request() {
  "${compose[@]}" exec -T app bash -c '
    IFS= read -r -d "" body || true
    export LC_ALL=C
    exec 3<>/dev/tcp/127.0.0.1/8080 || exit 9
    printf "%s %s HTTP/1.0\r\nHost: localhost\r\nAuthorization: Bearer %s\r\nContent-Type: application/json\r\nContent-Length: %d\r\n\r\n%s" "$1" "$2" "$YOZMNEWS_ADMIN_TOKEN" "${#body}" "$body" >&3
    IFS=" " read -r _ status _ <&3
    status=${status%$'"'"'\r'"'"'}
    while IFS= read -r line <&3 && [[ -n ${line%$'"'"'\r'"'"'} ]]; do :; done
    printf "%s\n" "$status"
    cat <&3
  ' admin "$1" "$2"
}
# DB 조회 한 줄 — 계정 · DB 이름은 postgres 컨테이너의 정의(deploy/compose.yaml 의 x-database)를 쓴다. 조회 오류는 표준 오류로 보이고 종료 코드가 0 이 아니다
db_query() {
  "${compose[@]}" exec -T postgres bash -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X -A -t -c "$1"' db "$1"
}
k14="K14 로컬 모델 표현 한 건"
feeds_up=0
if "${compose[@]}" ps --services --status running 2>/dev/null | grep -qx feeds; then feeds_up=1; fi
if [[ $token_read == 1 && $token_marker == set && $feeds_up == 1 ]]; then
  expected_model=$(sed -n 's/^model-version=//p' "$repo_dir/backend/embedding-model.properties")
  profile_json=$(sed -e ':a' -e 'N' -e '$!ba' -e 's/\n/\\n/g' "$deploy_dir/smoke/smoke-profile.yaml")
  registration=$(admin_request POST /api/admin/sources <<<"{\"profile\":\"$profile_json\"}")
  if [[ $(head -n 1 <<<"$registration") != 200 ]] || ! grep -qE '"result":"(REGISTERED|ALREADY_REGISTERED)"' <<<"$registration"; then
    fail "$k14" "시험 소스 등록 200 REGISTERED" "$(tr '\n' ' ' <<<"$registration" | head -c 300)"
  else
    source_id=$(db_query "select id from sources where profile like '%http://feeds/smoke.xml%' order by id limit 1")
    feed_id=$(db_query "select id from feeds where source_id = ${source_id:-0} order by id limit 1")
    source_enable=$(admin_request POST "/api/admin/sources/$source_id/enable" </dev/null)
    feed_enable=$(admin_request POST "/api/admin/feeds/$feed_id/enable" </dev/null)
    if ! grep -q '"result":"ENABLED"' <<<"$source_enable" || ! grep -q '"result":"ENABLED"' <<<"$feed_enable"; then
      fail "$k14" "소스 · 피드 켜기 ENABLED" "소스 $(tr '\n' ' ' <<<"$source_enable" | head -c 200) / 피드 $(tr '\n' ' ' <<<"$feed_enable" | head -c 300)"
    else
      # 수집(1분 주기) → 사건 묶기 대기(1분 주기) → 표현까지 최대 5분 기다린다 — 작업마다 백그라운드 작업 서버의 픽업 지연이 더해진다
      # 기사는 피드별 기록(article_feed_entries)으로 시험 피드에 잇는다. 조회가 실패하면 기다리지 않고 실패한다
      model=""
      query_failed=0
      started=$SECONDS
      for _ in $(seq 1 60); do
        if ! model=$(db_query "select f.model_version from article_match_features f join article_feed_entries e on e.article_id = f.article_id where e.feed_id = $feed_id order by f.created_at limit 1"); then
          query_failed=1
          break
        fi
        [[ -n $model ]] && break
        sleep 5
      done
      if ((query_failed)); then
        fail "$k14" "표현 행 조회 성공" "조회 실패(위 오류)"
      elif [[ -n $expected_model && $model == "$expected_model" ]]; then
        pass "$k14 ($model, 켜고 $((SECONDS - started))초)"
      else
        fail "$k14" "${expected_model:-정본 파일에 model-version 없음}" "${model:-5분 안에 표현 행이 없다}"
        # 어느 단계에서 멈췄는지 — 시험 피드에서 수집한 기사 수 · 그중 사건 묶기 대기 수 · 피드의 마지막 수집
        printf '      기사 %s · 사건 묶기 대기 %s · 마지막 수집 %s\n' \
          "$(db_query "select count(*) from article_feed_entries where feed_id = $feed_id")" \
          "$(db_query "select count(*) from event_intake_queue q join article_feed_entries e on e.article_id = q.article_id where e.feed_id = $feed_id")" \
          "$(db_query "select coalesce(last_polled_at::text, '없음') from feed_poll_states where feed_id = $feed_id")"
        "${compose[@]}" logs --no-color app 2>&1 | grep -iE 'onnx|임베더|embed' | tail -n 5
      fi
    fi
  fi
elif [[ ${SMOKE_REQUIRE_K14:-} == 1 ]]; then
  fail "$k14" "시험 피드 서버(SMOKE_OVERLAY)와 운영자 토큰" "시험 피드 서버 실행 ${feeds_up} · 토큰 ${token_marker:-읽지 못함}"
else
  skip "$k14" "시험 피드 서버(SMOKE_OVERLAY) 또는 운영자 토큰이 없다"
fi

if ((failures > 0)); then
  printf '%d 항목 실패\n' "$failures"
  exit 1
fi
printf '전 항목 통과\n'
