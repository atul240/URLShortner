#!/usr/bin/env bash
# Functional (positive + negative) tests for URLShortner.
# Requires the stack to be running:  docker compose up -d
#
# Expected results follow the intended API behaviour. A FAIL means the app
# currently does not behave that way; the actual HTTP code is printed.
#
# Usage: tests/api-tests.sh            (BASE and REDIS_CONTAINER can be overridden)

BASE=${BASE:-http://localhost:8080}
REDIS_CONTAINER=${REDIS_CONTAINER:-url-shortner-redis}
RUN_ID=$(date +%s%N | tail -c 7)   # unique suffix so custom codes don't clash between runs

PASS=0
FAIL=0
LAST_BODY=""

# Rate-limit keys (rate:shorten:*, rate:redirect:*) would make functional tests
# fail with 429, so clear them before every request. Only touches rate:* keys.
reset_rate() {
  docker exec "$REDIS_CONTAINER" sh -c "redis-cli --scan --pattern 'rate:*' | xargs -r redis-cli DEL >/dev/null" 2>/dev/null
}

# check NAME EXPECTED_HTTP_CODE [curl args...]
check() {
  local name=$1 expected=$2
  shift 2
  reset_rate
  local body_file code
  body_file=$(mktemp)
  code=$(curl -s -m 10 -o "$body_file" -w '%{http_code}' "$@")
  LAST_BODY=$(cat "$body_file")
  rm -f "$body_file"
  if [ "$code" = "$expected" ]; then
    PASS=$((PASS + 1))
    echo "PASS  $name  (HTTP $code)"
  else
    FAIL=$((FAIL + 1))
    echo "FAIL  $name  (expected HTTP $expected, got $code)  body: $LAST_BODY"
  fi
}

# expect_body NAME SUBSTRING  -- checks LAST_BODY contains SUBSTRING
expect_body() {
  local name=$1 needle=$2
  if [[ "$LAST_BODY" == *"$needle"* ]]; then
    PASS=$((PASS + 1))
    echo "PASS  $name  (body contains '$needle')"
  else
    FAIL=$((FAIL + 1))
    echo "FAIL  $name  (body does not contain '$needle')  body: $LAST_BODY"
  fi
}

# expect_eq NAME ACTUAL EXPECTED
expect_eq() {
  local name=$1 actual=$2 expected=$3
  if [ "$actual" = "$expected" ]; then
    PASS=$((PASS + 1))
    echo "PASS  $name  ($actual)"
  else
    FAIL=$((FAIL + 1))
    echo "FAIL  $name  (expected '$expected', got '$actual')"
  fi
}

post_json() { curl -s -m 10 -o /dev/null -w '%{http_code}' -X POST "$BASE/api/shorten" -H "Content-Type: application/json" "$@"; }

echo "== POSITIVE CASES =="

# P1: generated short code
check "P1 shorten without custom code" 201 \
  -X POST "$BASE/api/shorten" -H "Content-Type: application/json" \
  -d '{"longUrl":"https://example.com/p1"}'
expect_body "P1 response is a full short URL" "$BASE/"
GEN_CODE=${LAST_BODY#"$BASE/"}

# P2: custom short code
CUSTOM="p2-$RUN_ID"
check "P2 shorten with custom code" 201 \
  -X POST "$BASE/api/shorten" -H "Content-Type: application/json" \
  -d "{\"longUrl\":\"https://www.flipkart.com/iphone\",\"customCode\":\"$CUSTOM\"}"
expect_body "P2 response ends with custom code" "/$CUSTOM"

# P3: redirect for an existing code -> 302 with Location = long URL
check "P3 redirect existing code" 302 "$BASE/$CUSTOM"
LOC=$(reset_rate; curl -s -m 10 -o /dev/null -w '%{redirect_url}' "$BASE/$CUSTOM")
expect_eq "P3 Location header is the long URL" "$LOC" "https://www.flipkart.com/iphone"

# P4: long URL with query string is preserved exactly on redirect
QS_URL="https://example.com/search?q=url+shortener&lang=en"
check "P4 shorten long URL with query string" 201 \
  -X POST "$BASE/api/shorten" -H "Content-Type: application/json" \
  -d "{\"longUrl\":\"$QS_URL\",\"customCode\":\"p4-$RUN_ID\"}"
LOC=$(reset_rate; curl -s -m 10 -o /dev/null -w '%{redirect_url}' "$BASE/p4-$RUN_ID")
expect_eq "P4 query string preserved in redirect" "$LOC" "$QS_URL"

# P5: generated codes are unique
CODES=()
for i in 1 2 3 4 5; do
  check "P5.$i shorten (uniqueness batch)" 201 \
    -X POST "$BASE/api/shorten" -H "Content-Type: application/json" \
    -d "{\"longUrl\":\"https://example.com/batch/$i\"}"
  CODES+=("${LAST_BODY##*/}")
done
UNIQUE=$(printf '%s\n' "${CODES[@]}" | sort -u | wc -l)
expect_eq "P5 five generated codes are unique" "$UNIQUE" "5"

# P6: click counter increments on each redirect
CLICK_CODE="p6-$RUN_ID"
check "P6 create code for click counting" 201 \
  -X POST "$BASE/api/shorten" -H "Content-Type: application/json" \
  -d "{\"longUrl\":\"https://example.com/clicks\",\"customCode\":\"$CLICK_CODE\"}"
check "P6 first redirect" 302 "$BASE/$CLICK_CODE"
check "P6 second redirect" 302 "$BASE/$CLICK_CODE"
check "P6 stats endpoint" 200 "$BASE/api/stats/$CLICK_CODE"
expect_body "P6 stats show Clicks: 2" "Clicks: 2"

# P7: stats for an existing code shows its long URL
expect_body "P7 stats show the long URL" "LongUrl: https://example.com/clicks"

echo
echo "== NEGATIVE CASES =="

# N1: missing longUrl
check "N1 POST without longUrl" 400 \
  -X POST "$BASE/api/shorten" -H "Content-Type: application/json" -d '{}'

# N2: malformed JSON
check "N2 POST malformed JSON" 400 \
  -X POST "$BASE/api/shorten" -H "Content-Type: application/json" -d '{"longUrl":'

# N3: empty body
check "N3 POST empty body" 400 \
  -X POST "$BASE/api/shorten" -H "Content-Type: application/json" -d ''

# N4: missing Content-Type
check "N4 POST without Content-Type" 415 \
  -X POST "$BASE/api/shorten" -d '{"longUrl":"https://example.com"}'

# N5: custom code already taken
check "N5 POST duplicate custom code" 409 \
  -X POST "$BASE/api/shorten" -H "Content-Type: application/json" \
  -d "{\"longUrl\":\"https://example.com/other\",\"customCode\":\"$CUSTOM\"}"

# N6: unknown short code
check "N6 redirect unknown code" 404 "$BASE/no-such-code-$RUN_ID"

# N7: stats for unknown code
check "N7 stats unknown code" 404 "$BASE/api/stats/no-such-code-$RUN_ID"

# N8: wrong HTTP method on shorten endpoint
check "N8 GET on POST-only endpoint" 405 "$BASE/api/shorten"

# N9: longUrl that is not a URL
check "N9 POST invalid longUrl 'not-a-url'" 400 \
  -X POST "$BASE/api/shorten" -H "Content-Type: application/json" -d '{"longUrl":"not-a-url"}'

# N10: empty longUrl string
check "N10 POST empty longUrl" 400 \
  -X POST "$BASE/api/shorten" -H "Content-Type: application/json" -d '{"longUrl":""}'

# N11: custom code with invalid characters
check "N11 POST custom code with invalid characters" 400 \
  -X POST "$BASE/api/shorten" -H "Content-Type: application/json" \
  -d '{"longUrl":"https://example.com","customCode":"bad code!"}'

# N12: unknown path
check "N12 unknown path" 404 "$BASE/a/b/c/does-not-exist"

echo
echo "== RATE LIMIT CASES (run last, they fill the limiter) =="

# N13: shorten allows 10 requests per window, the 11th is rejected
reset_rate
codes=()
for i in $(seq 1 10); do
  codes+=("$(post_json -d '{"longUrl":"https://example.com/rl"}')")
done
ok=$(printf '%s\n' "${codes[@]}" | grep -c '^201$')
expect_eq "N13 first 10 shorten requests allowed" "$ok" "10"
expect_eq "N13 11th shorten request rejected" \
  "$(post_json -d '{"longUrl":"https://example.com/rl"}')" "429"

# N14: redirect allows 100 requests per window, the 101st is rejected
reset_rate
curl -s -m 10 -o /dev/null -X POST "$BASE/api/shorten" -H "Content-Type: application/json" \
  -d "{\"longUrl\":\"https://example.com/redir\",\"customCode\":\"rl-redir-$RUN_ID\"}"
reset_rate
codes=()
for i in $(seq 1 100); do
  codes+=("$(curl -s -m 10 -o /dev/null -w '%{http_code}' "$BASE/rl-redir-$RUN_ID")")
done
ok=$(printf '%s\n' "${codes[@]}" | grep -c '^302$')
expect_eq "N14 first 100 redirects allowed" "$ok" "100"
expect_eq "N14 101st redirect rejected" \
  "$(curl -s -m 10 -o /dev/null -w '%{http_code}' "$BASE/rl-redir-$RUN_ID")" "429"

reset_rate
echo
echo "Passed: $PASS   Failed: $FAIL"
[ "$FAIL" -eq 0 ]
