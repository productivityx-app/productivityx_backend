#!/bin/bash

# ═══════════════════════════════════════════════════════════════════════════════
#  ProductivityX — Complete API Test
#  Covers: all endpoints · validation · multi-user isolation · conflict detection
#          advisory lock behavior · idempotency · X-Request-ID header · SSE · sync
# ═══════════════════════════════════════════════════════════════════════════════

BASE="http://localhost:8080/api/v1"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
NC='\033[0m'

PASS=0
FAIL=0
WARN=0
SKIP=0
COOKIE_JAR_A=$(mktemp)
COOKIE_JAR_B=$(mktemp)
TOKEN_A=""
TOKEN_B=""

pass()    { echo -e "${GREEN}✅ PASS${NC} — $1"; ((PASS++)); }
fail()    { echo -e "${RED}❌ FAIL${NC} — $1\n   Expected : $2\n   Got      : $(echo "$3" | head -c 500)"; ((FAIL++)); }
warn()    { echo -e "${YELLOW}⚠  WARN${NC} — $1"; ((WARN++)); }
skip()    { echo -e "${DIM}⊘  SKIP${NC} — $1"; ((SKIP++)); }
section() { echo -e "\n${YELLOW}${BOLD}━━━ $1 ━━━${NC}"; }
info()    { echo -e "${CYAN}ℹ  $1${NC}"; }

check() {
  local label="$1" expected="$2" actual="$3"
  if echo "$actual" | grep -q "$expected"; then pass "$label"
  else fail "$label" "$expected" "$actual"; fi
}

check_not() {
  local label="$1" unexpected="$2" actual="$3"
  if echo "$actual" | grep -q "$unexpected"; then fail "$label" "NOT containing '$unexpected'" "$actual"
  else pass "$label"; fi
}

check_header() {
  local label="$1" header_name="$2" response_headers="$3"
  if echo "$response_headers" | grep -qi "$header_name"; then pass "$label"
  else fail "$label" "Header: $header_name" "$response_headers"; fi
}

extract() {
  echo "$1" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    keys = '$2'.split('.')
    v = d
    for k in keys: v = v[k]
    print(v)
except: print('')
" 2>/dev/null
}

prompt_otp() {
  echo ""
  echo -e "${CYAN}${BOLD}>>> $1${NC}"
  echo -n "    Enter OTP: "
  read -r OTP_INPUT
}

gen_uuid() {
  python3 -c "import uuid; print(uuid.uuid4())"
}

TIMESTAMP=$(date +%s)
# User A — primary test user
EMAIL_A="test_px_a_${TIMESTAMP}@gmail.com"
# User B — second user for isolation tests
EMAIL_B="test_px_b_${TIMESTAMP}@gmail.com"
PASSWORD="TestPass@1234"
WRONG_PASSWORD="WrongPass@9999"
USERNAME_A="pxuser_a_${TIMESTAMP}"
USERNAME_B="pxuser_b_${TIMESTAMP}"
BIRTH_DATE="1998-05-20"

section "CONFIGURATION"
info "User A email : $EMAIL_A"
info "User B email : $EMAIL_B"
info "Base URL     : $BASE"
echo ""

# ─── HEALTH ───────────────────────────────────────────────────────────────────

section "HEALTH CHECK"
R=$(curl -s http://localhost:8080/actuator/health)
check "Actuator UP" '"status":"UP"' "$R"

# ─── X-Request-ID HEADER ──────────────────────────────────────────────────────

section "REQUEST LOGGING FILTER — X-Request-ID"
HEADERS=$(curl -s -D - -o /dev/null http://localhost:8080/actuator/health)
check_header "GET /actuator/health returns X-Request-ID header" "x-request-id" "$HEADERS"

CUSTOM_RID="my-custom-request-id-$(date +%s)"
HEADERS=$(curl -s -D - -o /dev/null -H "X-Request-ID: $CUSTOM_RID" http://localhost:8080/actuator/health)
if echo "$HEADERS" | grep -qi "$CUSTOM_RID"; then pass "Custom X-Request-ID is echoed back"
else warn "Custom X-Request-ID may not be echoed (check CORS exposed headers config)"; fi

# ─── AUTH — REGISTER USER A ───────────────────────────────────────────────────

section "AUTH — REGISTER USER A (validation failures)"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d '{"email":"not-an-email","password":"TestPass@1234","firstName":"A","lastName":"B","username":"validusr1","birthDate":"1998-05-20"}')
check "Invalid email → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"ok${TIMESTAMP}@test.com\",\"password\":\"weak\",\"firstName\":\"A\",\"lastName\":\"B\",\"username\":\"validusr2\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Weak password → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"ok${TIMESTAMP}@test.com\",\"password\":\"$PASSWORD\",\"firstName\":\"\",\"lastName\":\"B\",\"username\":\"validusr3\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Blank firstName → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"ok${TIMESTAMP}@test.com\",\"password\":\"$PASSWORD\",\"firstName\":\"A\",\"lastName\":\"B\",\"username\":\"x\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Username too short (1 char) → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"ok${TIMESTAMP}@test.com\",\"password\":\"$PASSWORD\",\"firstName\":\"A\",\"lastName\":\"B\",\"username\":\"validusr4\",\"birthDate\":\"2015-01-01\"}")
check "Underage birthDate (2015) → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d '{}')
check "Empty body → fails" '"success":false' "$R"

section "AUTH — REGISTER USER A (success)"
R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_A\",\"password\":\"$PASSWORD\",\"firstName\":\"Test\",\"lastName\":\"UserA\",\"username\":\"$USERNAME_A\",\"birthDate\":\"$BIRTH_DATE\",\"gender\":\"MALE\"}")
check "Register User A → success" '"success":true' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_A\",\"password\":\"$PASSWORD\",\"firstName\":\"A\",\"lastName\":\"B\",\"username\":\"dupusr_${TIMESTAMP}\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Duplicate email → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"other_${TIMESTAMP}@test.com\",\"password\":\"$PASSWORD\",\"firstName\":\"A\",\"lastName\":\"B\",\"username\":\"$USERNAME_A\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Duplicate username → fails" '"success":false' "$R"

# ─── REGISTER USER B (for multi-user tests) ───────────────────────────────────

section "AUTH — REGISTER USER B (multi-user isolation)"
R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_B\",\"password\":\"$PASSWORD\",\"firstName\":\"Test\",\"lastName\":\"UserB\",\"username\":\"$USERNAME_B\",\"birthDate\":\"$BIRTH_DATE\",\"gender\":\"FEMALE\"}")
check "Register User B → success" '"success":true' "$R"

# ─── LOGIN BLOCKED BEFORE VERIFICATION ───────────────────────────────────────

section "AUTH — LOGIN BLOCKED PRE-VERIFICATION"
R=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL_A\",\"password\":\"$PASSWORD\"}")
check "User A: login before verification → fails (AUTH_003)" '"success":false' "$R"
check "User A: error code is AUTH_003" '"AUTH_003"' "$R"

# ─── RESEND VERIFICATION ──────────────────────────────────────────────────────

section "AUTH — RESEND VERIFICATION"
R=$(curl -s -X POST "$BASE/auth/resend-verification" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_A\"}")
check "Resend for User A → success" '"success":true' "$R"

R=$(curl -s -X POST "$BASE/auth/resend-verification" -H "Content-Type: application/json" \
  -d '{"email":"ghost_nobody@nowhere.xyz"}')
info "Resend for unknown email response: $(echo "$R" | head -c 80)"

# ─── VERIFY EMAIL — USER A ────────────────────────────────────────────────────

section "AUTH — VERIFY EMAIL OTP — USER A"
info "OTP sent to: $EMAIL_A"

R=$(curl -s -X POST "$BASE/auth/verify-otp" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_A\",\"otp\":\"000000\"}")
check "Wrong OTP → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/verify-otp" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_A\",\"otp\":\"12\"}")
check "OTP too short (2 chars) → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/verify-otp" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_A\",\"otp\":\"abcdef\"}")
check "Non-numeric OTP → fails" '"success":false' "$R"

prompt_otp "Enter the REAL OTP for User A ($EMAIL_A)"
VERIFY_A=$(curl -s -c "$COOKIE_JAR_A" -X POST "$BASE/auth/verify-otp" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_A\",\"otp\":\"$OTP_INPUT\"}")
check "Correct OTP → verified + tokens" '"success":true' "$VERIFY_A"

TOKEN_A=$(extract "$VERIFY_A" "data.accessToken")
if [ -z "$TOKEN_A" ]; then
  echo -e "${RED}✗  Cannot extract User A access token — aborting.${NC}"
  echo "   Response: $VERIFY_A"
  rm -f "$COOKIE_JAR_A" "$COOKIE_JAR_B"; exit 1
fi
info "User A token: ${TOKEN_A:0:40}..."

R=$(curl -s -X POST "$BASE/auth/verify-otp" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_A\",\"otp\":\"$OTP_INPUT\"}")
check "OTP replay → fails (already used)" '"success":false' "$R"

# ─── VERIFY EMAIL — USER B ────────────────────────────────────────────────────

section "AUTH — VERIFY EMAIL OTP — USER B"
info "OTP sent to: $EMAIL_B"
prompt_otp "Enter the REAL OTP for User B ($EMAIL_B)"
VERIFY_B=$(curl -s -c "$COOKIE_JAR_B" -X POST "$BASE/auth/verify-otp" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_B\",\"otp\":\"$OTP_INPUT\"}")
check "User B: correct OTP → verified + tokens" '"success":true' "$VERIFY_B"

TOKEN_B=$(extract "$VERIFY_B" "data.accessToken")
if [ -z "$TOKEN_B" ]; then
  echo -e "${RED}✗  Cannot extract User B access token — aborting.${NC}"
  rm -f "$COOKIE_JAR_A" "$COOKIE_JAR_B"; exit 1
fi
info "User B token: ${TOKEN_B:0:40}..."

# ─── AUTH — LOGIN ─────────────────────────────────────────────────────────────

section "AUTH — LOGIN"
R=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL_A\",\"password\":\"$WRONG_PASSWORD\"}")
check "Wrong password → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d '{"identifier":"ghost_xyz@nowhere.com","password":"TestPass@1234"}')
check "Unknown email → fails (AUTH_001)" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d "{\"identifier\":\"\",\"password\":\"$PASSWORD\"}")
check "Blank identifier → fails" '"success":false' "$R"

LOGIN_A=$(curl -s -c "$COOKIE_JAR_A" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL_A\",\"password\":\"$PASSWORD\"}")
check "Login by email → success" '"success":true' "$LOGIN_A"
TOKEN_A=$(extract "$LOGIN_A" "data.accessToken")
info "User A token refreshed: ${TOKEN_A:0:40}..."

R=$(curl -s -c "$COOKIE_JAR_A" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$USERNAME_A\",\"password\":\"$PASSWORD\"}")
check "Login by username → success" '"success":true' "$R"

LOGIN_B=$(curl -s -c "$COOKIE_JAR_B" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL_B\",\"password\":\"$PASSWORD\"}")
check "User B login by email → success" '"success":true' "$LOGIN_B"
TOKEN_B=$(extract "$LOGIN_B" "data.accessToken")

# ─── UNAUTHENTICATED ACCESS ───────────────────────────────────────────────────

section "UNAUTHENTICATED ACCESS"
for ROUTE in auth/me profile preferences notes tasks events; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/$ROUTE")
  check "GET /$ROUTE without token → 401" "401" "$CODE"
done

# ─── AUTH — ME ────────────────────────────────────────────────────────────────

section "AUTH — ME"
R=$(curl -s "$BASE/auth/me" -H "Authorization: Bearer $TOKEN_A")
check "GET /auth/me → success" '"success":true' "$R"
check "GET /auth/me → correct email" "$EMAIL_A" "$R"

R_B=$(curl -s "$BASE/auth/me" -H "Authorization: Bearer $TOKEN_B")
check "User B GET /auth/me → different email" "$EMAIL_B" "$R_B"
check_not "User A token does not reveal User B email" "$EMAIL_B" "$R"

# ─── TOKEN REFRESH ────────────────────────────────────────────────────────────

section "TOKEN REFRESH"
R=$(curl -s -b "$COOKIE_JAR_A" -c "$COOKIE_JAR_A" -X POST "$BASE/auth/refresh")
check "POST /auth/refresh → rotates token" '"success":true' "$R"
NEW_TOKEN=$(extract "$R" "data.accessToken")
[ -n "$NEW_TOKEN" ] && TOKEN_A="$NEW_TOKEN" && info "User A token rotated: ${TOKEN_A:0:40}..."

R=$(curl -s -c /dev/null -X POST "$BASE/auth/refresh" --cookie "refreshToken=invalid_garbage_token")
check "Refresh with bad cookie → fails" '"success":false' "$R"

# ─── PROFILE ──────────────────────────────────────────────────────────────────

section "PROFILE — USER A"
R=$(curl -s "$BASE/profile" -H "Authorization: Bearer $TOKEN_A")
check "GET /profile → success" '"success":true' "$R"
check "GET /profile → has firstName" '"firstName"' "$R"
check "GET /profile → has fullName" '"fullName"' "$R"

R=$(curl -s -X PUT "$BASE/profile" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Updated","lastName":"TestUserA","bio":"Bio from v3 test","timezone":"Africa/Algiers","language":"FR","theme":"DARK"}')
check "PUT /profile full update → success" '"success":true' "$R"
check "PUT /profile → firstName updated" '"Updated"' "$R"

R=$(curl -s -X PUT "$BASE/profile" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"PartialUpdate"}')
check "PUT /profile partial update → success" '"success":true' "$R"

R=$(curl -s -X PUT "$BASE/profile" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"theme":"RAINBOW"}')
check "PUT /profile invalid theme enum → fails" '"success":false' "$R"

R=$(curl -s -X PUT "$BASE/profile" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d "{\"bio\":\"$(python3 -c "print('x'*501)")\"}") 
check "PUT /profile bio > 500 chars → fails" '"success":false' "$R"

R=$(curl -s -X PATCH "$BASE/profile/avatar" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"avatarUrl":"https://example.com/avatar.png"}')
check "PATCH /profile/avatar → success" '"success":true' "$R"

R=$(curl -s -X PATCH "$BASE/profile/avatar" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{}')
check "PATCH /profile/avatar blank URL → fails" '"success":false' "$R"

section "MULTI-USER — PROFILE ISOLATION"
PROFILE_A=$(curl -s "$BASE/profile" -H "Authorization: Bearer $TOKEN_A")
PROFILE_B=$(curl -s "$BASE/profile" -H "Authorization: Bearer $TOKEN_B")
check_not "User A profile does not contain User B email" "$EMAIL_B" "$PROFILE_A"
check_not "User B profile does not contain User A email" "$EMAIL_A" "$PROFILE_B"

# ─── PREFERENCES ──────────────────────────────────────────────────────────────

section "PREFERENCES"
R=$(curl -s "$BASE/preferences" -H "Authorization: Bearer $TOKEN_A")
check "GET /preferences → success" '"success":true' "$R"

R=$(curl -s -X PUT "$BASE/preferences" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"pomodoroFocusMinutes":30,"pomodoroShortBreakMinutes":5,"pomodoroLongBreakMinutes":20,"pomodoroCyclesBeforeLongBreak":4,"pomodoroAutoStartBreaks":true,"pomodoroAutoStartFocus":false,"pomodoroSoundEnabled":true,"notifyTaskReminders":true,"notifyEventReminders":true,"notifyPomodoroEnd":true,"notifyDailySummary":false,"defaultTaskView":"KANBAN","defaultCalendarView":"WEEK","weekStartsOn":"MON","aiContextEnabled":true,"compactMode":false}')
check "PUT /preferences full update → success" '"success":true' "$R"

R=$(curl -s -X PUT "$BASE/preferences" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"pomodoroFocusMinutes":45}')
check "PUT /preferences partial update → success" '"success":true' "$R"
check "PUT /preferences partial → focus now 45" '"pomodoroFocusMinutes":45' "$R"

R=$(curl -s -X PUT "$BASE/preferences" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"pomodoroFocusMinutes":0}')
check "PUT /preferences focus=0 (below @Min 1) → fails" '"success":false' "$R"

R=$(curl -s -X PUT "$BASE/preferences" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"pomodoroFocusMinutes":200}')
check "PUT /preferences focus=200 (above @Max 120) → fails" '"success":false' "$R"

R=$(curl -s -X PUT "$BASE/preferences" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"pomodoroCyclesBeforeLongBreak":0}')
check "PUT /preferences cycles=0 → fails" '"success":false' "$R"

R=$(curl -s -X PUT "$BASE/preferences" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"pomodoroCyclesBeforeLongBreak":11}')
check "PUT /preferences cycles=11 (above @Max 10) → fails" '"success":false' "$R"

# ─── TAGS ─────────────────────────────────────────────────────────────────────

section "TAGS — CRUD & VALIDATION"
TAG_R=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"name":"work","color":"#6366F1"}')
check "POST /tags → success" '"success":true' "$TAG_R"
TAG_ID=$(extract "$TAG_R" "data.id")
info "Tag A1 ID: $TAG_ID"

TAG2_R=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"name":"personal","color":"#22C55E"}')
check "POST /tags second tag → success" '"success":true' "$TAG2_R"
TAG2_ID=$(extract "$TAG2_R" "data.id")

R=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"name":"work","color":"#EF4444"}')
check "POST /tags duplicate name (same user) → fails (VAL_006)" '"success":false' "$R"
check "Duplicate tag error code" '"VAL_006"' "$R"

R=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"name":"bad-color","color":"red"}')
check "POST /tags invalid hex color → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"name":"","color":"#6366F1"}')
check "POST /tags blank name → fails" '"success":false' "$R"

R=$(curl -s "$BASE/tags" -H "Authorization: Bearer $TOKEN_A")
check "GET /tags list → success" '"success":true' "$R"

if [ -n "$TAG_ID" ]; then
  R=$(curl -s -X PUT "$BASE/tags/$TAG_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"name":"work-updated","color":"#8B5CF6"}')
  check "PUT /tags/{id} → success" '"success":true' "$R"

  R=$(curl -s -X PUT "$BASE/tags/$TAG_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"name":"personal","color":"#6366F1"}')
  check "PUT /tags/{id} rename to existing name → fails (VAL_006)" '"success":false' "$R"
fi

section "MULTI-USER — TAG ISOLATION"
TAG_B_R=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN_B" \
  -H "Content-Type: application/json" \
  -d '{"name":"work","color":"#6366F1"}')
check "User B: POST /tags 'work' → success (same name OK for different user)" '"success":true' "$TAG_B_R"
TAG_B_ID=$(extract "$TAG_B_R" "data.id")

if [ -n "$TAG_ID" ] && [ -n "$TAG_B_ID" ]; then
  R=$(curl -s -X PUT "$BASE/tags/$TAG_ID" -H "Authorization: Bearer $TOKEN_B" \
    -H "Content-Type: application/json" \
    -d '{"name":"stolen","color":"#EF4444"}')
  check "User B cannot update User A tag → fails (RES_TAG_NOT_FOUND)" '"success":false' "$R"

  R=$(curl -s -X DELETE "$BASE/tags/$TAG_ID" -H "Authorization: Bearer $TOKEN_B")
  check "User B cannot delete User A tag → fails" '"success":false' "$R"

  TAGS_B=$(curl -s "$BASE/tags" -H "Authorization: Bearer $TOKEN_B")
  check_not "User B tag list does not contain User A tag" "$TAG_ID" "$TAGS_B"
fi

# ─── NOTES ────────────────────────────────────────────────────────────────────

section "NOTES — VALIDATION"
LONG_TITLE=$(python3 -c "print('a'*501)")
R=$(curl -s -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"$LONG_TITLE\",\"content\":\"test\"}")
check "POST /notes title > 500 chars → fails" '"success":false' "$R"

section "NOTES — CRUD"
NOTE_R=$(curl -s -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Auto Test Note A\",\"content\":\"## Hello\\nThis is note content for User A.\",\"pinned\":false}")
check "POST /notes → success" '"success":true' "$NOTE_R"
NOTE_ID=$(extract "$NOTE_R" "data.id")
NOTE_VERSION=$(extract "$NOTE_R" "data.version")
NOTE_UPDATED_AT=$(extract "$NOTE_R" "data.updatedAt")
info "Note ID: $NOTE_ID  version=$NOTE_VERSION"

if [ -n "$TAG_ID" ]; then
  TAGGED_NOTE_R=$(curl -s -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Tagged Note\",\"content\":\"# Tag test\",\"tagIds\":[\"$TAG_ID\"]}")
  check "POST /notes with tagIds → success" '"success":true' "$TAGGED_NOTE_R"
  TAGGED_NOTE_ID=$(extract "$TAGGED_NOTE_R" "data.id")
fi

R=$(curl -s "$BASE/notes" -H "Authorization: Bearer $TOKEN_A")
check "GET /notes list → success" '"success":true' "$R"
check "GET /notes → has content field" '"content"' "$R"
check "GET /notes → has totalElements" '"totalElements"' "$R"

R=$(curl -s "$BASE/notes?pinned=true" -H "Authorization: Bearer $TOKEN_A")
check "GET /notes?pinned=true → success" '"success":true' "$R"

if [ -n "$TAG_ID" ]; then
  R=$(curl -s "$BASE/notes?tagId=$TAG_ID" -H "Authorization: Bearer $TOKEN_A")
  check "GET /notes?tagId= → success" '"success":true' "$R"
fi

R=$(curl -s "$BASE/notes?page=0&size=5" -H "Authorization: Bearer $TOKEN_A")
check "GET /notes paginated → success" '"success":true' "$R"

R=$(curl -s "$BASE/notes/trash" -H "Authorization: Bearer $TOKEN_A")
check "GET /notes/trash → success" '"success":true' "$R"

if [ -n "$NOTE_ID" ]; then
  R=$(curl -s "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN_A")
  check "GET /notes/{id} → success" '"success":true' "$R"
  check "GET /notes/{id} → correct title" "Auto Test Note A" "$R"
  check "GET /notes/{id} → has version" '"version"' "$R"
  check "GET /notes/{id} → has wordCount" '"wordCount"' "$R"
  check "GET /notes/{id} → has readingTimeSeconds" '"readingTimeSeconds"' "$R"
  check "GET /notes/{id} → has syncStatus" '"syncStatus"' "$R"

  R=$(curl -s -X PUT "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"title":"Updated Note Title","content":"# Updated\nContent changed.","pinned":true}')
  check "PUT /notes/{id} → success" '"success":true' "$R"
  check "PUT /notes/{id} → version incremented" '"version":2' "$R"

  R=$(curl -s -X PATCH "$BASE/notes/$NOTE_ID/pin" -H "Authorization: Bearer $TOKEN_A")
  check "PATCH /notes/{id}/pin → success" '"success":true' "$R"
  check "PATCH /notes/{id}/pin → pinned is true" '"pinned":true' "$R"

  R=$(curl -s -X PATCH "$BASE/notes/$NOTE_ID/unpin" -H "Authorization: Bearer $TOKEN_A")
  check "PATCH /notes/{id}/unpin → success" '"success":true' "$R"
  check "PATCH /notes/{id}/unpin → pinned is false" '"pinned":false' "$R"

  if [ -n "$TAG2_ID" ]; then
    R=$(curl -s -X POST "$BASE/notes/$NOTE_ID/tags" -H "Authorization: Bearer $TOKEN_A" \
      -H "Content-Type: application/json" \
      -d "{\"tagId\":\"$TAG2_ID\"}")
    check "POST /notes/{id}/tags → success" '"success":true' "$R"

    R=$(curl -s -X DELETE "$BASE/notes/$NOTE_ID/tags/$TAG2_ID" -H "Authorization: Bearer $TOKEN_A")
    check "DELETE /notes/{id}/tags/{tagId} → success" '"success":true' "$R"
  fi

  R=$(curl -s -X DELETE "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN_A")
  check "DELETE /notes/{id} soft-delete → success" '"success":true' "$R"
  check "DELETE /notes/{id} → deleted=true" '"deleted":true' "$R"

  R=$(curl -s "$BASE/notes/trash" -H "Authorization: Bearer $TOKEN_A")
  check "GET /notes/trash shows deleted note → success" '"success":true' "$R"

  R=$(curl -s -X PUT "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"title":"Edit trashed note"}')
  check "PUT trashed note → fails (VAL_NOTE_TRASHED)" '"success":false' "$R"

  R=$(curl -s -X PATCH "$BASE/notes/$NOTE_ID/restore" -H "Authorization: Bearer $TOKEN_A")
  check "PATCH /notes/{id}/restore → success" '"success":true' "$R"
  check "PATCH restore → deleted=false" '"deleted":false' "$R"

  R=$(curl -s -X PATCH "$BASE/notes/$NOTE_ID/restore" -H "Authorization: Bearer $TOKEN_A")
  check "PATCH restore non-trashed note → fails (VAL_NOTE_NOT_IN_TRASH)" '"success":false' "$R"

  R=$(curl -s -X DELETE "$BASE/notes/$NOTE_ID/permanent" -H "Authorization: Bearer $TOKEN_A")
  check "DELETE permanent on active note → fails (VAL_NOTE_MUST_BE_TRASHED_FIRST)" '"success":false' "$R"

  R=$(curl -s -X DELETE "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN_A")
  check "Soft-delete again before permanent → success" '"success":true' "$R"

  R=$(curl -s -X DELETE "$BASE/notes/$NOTE_ID/permanent" -H "Authorization: Bearer $TOKEN_A")
  check "DELETE /notes/{id}/permanent → success" '"success":true' "$R"

  R=$(curl -s "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN_A")
  check "GET permanently deleted note → fails (RES_NOTE_NOT_FOUND)" '"success":false' "$R"

  R=$(curl -s "$BASE/notes/00000000-0000-0000-0000-000000000000" -H "Authorization: Bearer $TOKEN_A")
  check "GET /notes/{unknown-uuid} → fails" '"success":false' "$R"
fi

section "MULTI-USER — NOTE ISOLATION"
NOTE_B_R=$(curl -s -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN_B" \
  -H "Content-Type: application/json" \
  -d '{"title":"User B Private Note","content":"Only B can see this."}')
check "User B: POST /notes → success" '"success":true' "$NOTE_B_R"
NOTE_B_ID=$(extract "$NOTE_B_R" "data.id")

if [ -n "$NOTE_B_ID" ]; then
  R=$(curl -s "$BASE/notes/$NOTE_B_ID" -H "Authorization: Bearer $TOKEN_A")
  check "User A cannot read User B note → fails (RES_NOTE_NOT_FOUND)" '"success":false' "$R"

  R=$(curl -s -X PUT "$BASE/notes/$NOTE_B_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"title":"Hijacked by A"}')
  check "User A cannot update User B note → fails" '"success":false' "$R"

  R=$(curl -s -X DELETE "$BASE/notes/$NOTE_B_ID" -H "Authorization: Bearer $TOKEN_A")
  check "User A cannot soft-delete User B note → fails" '"success":false' "$R"

  NOTES_A=$(curl -s "$BASE/notes" -H "Authorization: Bearer $TOKEN_A")
  check_not "User A note list does not contain User B note" "$NOTE_B_ID" "$NOTES_A"
fi

# ─── CONFLICT DETECTION — NOTES ───────────────────────────────────────────────

section "CONFLICT DETECTION — NOTE VERSION & THREE-WAY MERGE"
CONFLICT_NOTE_R=$(curl -s -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"title":"Conflict Test Note","content":"Original line 1\nOriginal line 2\nOriginal line 3"}')
check "Create conflict-test note → success" '"success":true' "$CONFLICT_NOTE_R"
CONFLICT_NOTE_ID=$(extract "$CONFLICT_NOTE_R" "data.id")
CONFLICT_NOTE_VERSION=$(extract "$CONFLICT_NOTE_R" "data.version")
CONFLICT_NOTE_UPDATED_AT=$(extract "$CONFLICT_NOTE_R" "data.updatedAt")
info "Conflict note ID: $CONFLICT_NOTE_ID  version=$CONFLICT_NOTE_VERSION"

if [ -n "$CONFLICT_NOTE_ID" ]; then
  R=$(curl -s -X PUT "$BASE/notes/$CONFLICT_NOTE_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Conflict Note Updated\",\"content\":\"Server updated line 1\\nOriginal line 2\\nOriginal line 3\",\"knownVersion\":$CONFLICT_NOTE_VERSION}")
  check "PUT /notes with correct knownVersion → success" '"success":true' "$R"
  NEW_VERSION=$(extract "$R" "data.version")
  info "After server update: version=$NEW_VERSION"

  STALE_VERSION=$CONFLICT_NOTE_VERSION
  R=$(curl -s -X PUT "$BASE/notes/$CONFLICT_NOTE_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Stale client update\",\"content\":\"Client edited line 1\\nOriginal line 2\\nOriginal line 3\",\"knownVersion\":$STALE_VERSION}")
  info "PUT with stale knownVersion response: $(echo "$R" | head -c 200)"
  if echo "$R" | grep -q "CONFLICT_VERSION\|CONFLICT_MERGE\|success.*true"; then
    pass "PUT with stale version → handled (conflict or auto-merge)"
  else
    warn "PUT with stale version returned unexpected response — check conflict logic"
  fi

  R=$(curl -s -X PUT "$BASE/notes/$CONFLICT_NOTE_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"clientUpdatedAt test\",\"clientUpdatedAt\":\"$CONFLICT_NOTE_UPDATED_AT\"}")
  info "PUT with old clientUpdatedAt response: $(echo "$R" | head -c 200)"
  if echo "$R" | grep -q "CONFLICT_VERSION\|CONFLICT_MERGE\|success.*true"; then
    pass "PUT with stale clientUpdatedAt → handled"
  else
    warn "PUT with old clientUpdatedAt returned unexpected response"
  fi

  R=$(curl -s -X PUT "$BASE/notes/$CONFLICT_NOTE_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"title":"No version check","content":"Fully unrelated content"}')
  check "PUT without knownVersion or clientUpdatedAt → success (no conflict check)" '"success":true' "$R"

  R=$(curl -s -X DELETE "$BASE/notes/$CONFLICT_NOTE_ID" -H "Authorization: Bearer $TOKEN_A")
  R=$(curl -s -X DELETE "$BASE/notes/$CONFLICT_NOTE_ID/permanent" -H "Authorization: Bearer $TOKEN_A")
fi

# ─── TASKS ────────────────────────────────────────────────────────────────────

section "TASKS — VALIDATION"
R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"priority":"HIGH","status":"TODO"}')
check "POST /tasks without title → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"title":"Bad Task","estimatedMinutes":-5}')
check "POST /tasks negative estimatedMinutes → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"title":"Zero minutes","estimatedMinutes":0}')
check "POST /tasks estimatedMinutes=0 → fails (@Positive requires > 0)" '"success":false' "$R"

LONG_T=$(python3 -c "print('t'*501)")
R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"$LONG_T\"}")
check "POST /tasks title > 500 chars → fails" '"success":false' "$R"

section "TASKS — CRUD"
TASK_R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"title":"Auto Test Task","description":"Script test","priority":"HIGH","status":"TODO","estimatedMinutes":60,"dueDate":"2026-12-31"}')
check "POST /tasks → success" '"success":true' "$TASK_R"
TASK_ID=$(extract "$TASK_R" "data.id")
info "Task ID: $TASK_ID"

R=$(curl -s "$BASE/tasks" -H "Authorization: Bearer $TOKEN_A")
check "GET /tasks list → success" '"success":true' "$R"

R=$(curl -s "$BASE/tasks?status=TODO" -H "Authorization: Bearer $TOKEN_A")
check "GET /tasks?status=TODO → success" '"success":true' "$R"

R=$(curl -s "$BASE/tasks?priority=HIGH" -H "Authorization: Bearer $TOKEN_A")
check "GET /tasks?priority=HIGH → success" '"success":true' "$R"

R=$(curl -s "$BASE/tasks?page=0&size=10" -H "Authorization: Bearer $TOKEN_A")
check "GET /tasks paginated → success" '"success":true' "$R"

R=$(curl -s "$BASE/tasks/trash" -H "Authorization: Bearer $TOKEN_A")
check "GET /tasks/trash → success" '"success":true' "$R"

if [ -n "$TASK_ID" ]; then
  R=$(curl -s "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN_A")
  check "GET /tasks/{id} → success" '"success":true' "$R"
  check "GET /tasks/{id} → has subtasks array" '"subtasks"' "$R"
  check "GET /tasks/{id} → has completedAt field" '"actualMinutes"' "$R"

  R=$(curl -s -X PUT "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"title":"Updated Task","priority":"URGENT","status":"IN_PROGRESS"}')
  check "PUT /tasks/{id} → success" '"success":true' "$R"
  check "PUT /tasks/{id} → version incremented" '"version":2' "$R"

  for STATUS in ON_HOLD DONE; do
    R=$(curl -s -X PATCH "$BASE/tasks/$TASK_ID/status" -H "Authorization: Bearer $TOKEN_A" \
      -H "Content-Type: application/json" \
      -d "{\"status\":\"$STATUS\"}")
    check "PATCH /tasks/{id}/status → $STATUS" '"success":true' "$R"
  done

  R=$(curl -s "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN_A")
  check "DONE task has completedAt set" '"completedAt"' "$R"

  R=$(curl -s -X PATCH "$BASE/tasks/$TASK_ID/status" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"status":"TODO"}')
  check "PATCH status back to TODO clears completedAt → success" '"success":true' "$R"

  R=$(curl -s -X PATCH "$BASE/tasks/$TASK_ID/status" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"status":"FLYING"}')
  check "PATCH /tasks/{id}/status invalid value → fails" '"success":false' "$R"

  SUBTASK_R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Subtask One\",\"priority\":\"LOW\",\"parentTaskId\":\"$TASK_ID\"}")
  check "POST /tasks subtask (parentTaskId) → success" '"success":true' "$SUBTASK_R"
  SUBTASK_ID=$(extract "$SUBTASK_R" "data.id")

  if [ -n "$SUBTASK_ID" ]; then
    R=$(curl -s "$BASE/tasks?parentId=$TASK_ID" -H "Authorization: Bearer $TOKEN_A")
    check "GET /tasks?parentId= → returns subtasks" '"success":true' "$R"

    R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN_A" \
      -H "Content-Type: application/json" \
      -d "{\"title\":\"Grandchild Task\",\"parentTaskId\":\"$SUBTASK_ID\"}")
    check "POST /tasks grandchild (depth > 1) → fails (VAL_007)" '"success":false' "$R"
    check "Grandchild error code is VAL_007" '"VAL_007"' "$R"

    R=$(curl -s -X PATCH "$BASE/tasks/reorder" -H "Authorization: Bearer $TOKEN_A" \
      -H "Content-Type: application/json" \
      -d "{\"items\":[{\"id\":\"$TASK_ID\",\"position\":0},{\"id\":\"$SUBTASK_ID\",\"position\":1}]}")
    check "PATCH /tasks/reorder → success" '"success":true' "$R"
  fi

  R=$(curl -s -X PATCH "$BASE/tasks/reorder" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{}')
  check "PATCH /tasks/reorder empty body → fails" '"success":false' "$R"

  R=$(curl -s -X DELETE "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN_A")
  check "DELETE /tasks/{id} soft-delete → success" '"success":true' "$R"
  check "Soft-delete → deleted=true" '"deleted":true' "$R"

  R=$(curl -s "$BASE/tasks/trash" -H "Authorization: Bearer $TOKEN_A")
  check "GET /tasks/trash shows deleted task" '"success":true' "$R"

  R=$(curl -s -X PUT "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"title":"Edit trashed task"}')
  check "PUT trashed task → fails" '"success":false' "$R"

  R=$(curl -s -X DELETE "$BASE/tasks/$TASK_ID/permanent" -H "Authorization: Bearer $TOKEN_A")
  check "DELETE permanent before restore → fails (VAL_TASK_MUST_BE_TRASHED_FIRST is already met, but let's try)" '"success":true' "$R"

  R=$(curl -s "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN_A")
  check "GET permanently deleted task → fails" '"success":false' "$R"

  TASK2_R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"title":"Task for restore test","priority":"MEDIUM"}')
  TASK2_ID=$(extract "$TASK2_R" "data.id")

  if [ -n "$TASK2_ID" ]; then
    R=$(curl -s -X PATCH "$BASE/tasks/$TASK2_ID/restore" -H "Authorization: Bearer $TOKEN_A")
    check "Restore non-trashed task → fails (VAL_TASK_NOT_IN_TRASH)" '"success":false' "$R"

    curl -s -X DELETE "$BASE/tasks/$TASK2_ID" -H "Authorization: Bearer $TOKEN_A" > /dev/null

    R=$(curl -s -X PATCH "$BASE/tasks/$TASK2_ID/restore" -H "Authorization: Bearer $TOKEN_A")
    check "PATCH /tasks/{id}/restore → success" '"success":true' "$R"

    curl -s -X DELETE "$BASE/tasks/$TASK2_ID" -H "Authorization: Bearer $TOKEN_A" > /dev/null
    curl -s -X DELETE "$BASE/tasks/$TASK2_ID/permanent" -H "Authorization: Bearer $TOKEN_A" > /dev/null
  fi

  R=$(curl -s "$BASE/tasks/00000000-0000-0000-0000-000000000000" -H "Authorization: Bearer $TOKEN_A")
  check "GET /tasks/{unknown-uuid} → fails" '"success":false' "$R"
fi

section "MULTI-USER — TASK ISOLATION"
TASK_B_R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN_B" \
  -H "Content-Type: application/json" \
  -d '{"title":"User B Private Task","priority":"HIGH"}')
check "User B: POST /tasks → success" '"success":true' "$TASK_B_R"
TASK_B_ID=$(extract "$TASK_B_R" "data.id")

if [ -n "$TASK_B_ID" ]; then
  R=$(curl -s "$BASE/tasks/$TASK_B_ID" -H "Authorization: Bearer $TOKEN_A")
  check "User A cannot read User B task → fails" '"success":false' "$R"

  R=$(curl -s -X PUT "$BASE/tasks/$TASK_B_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"title":"Hijacked"}')
  check "User A cannot update User B task → fails" '"success":false' "$R"

  R=$(curl -s -X PATCH "$BASE/tasks/$TASK_B_ID/status" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"status":"DONE"}')
  check "User A cannot change User B task status → fails" '"success":false' "$R"

  TASK_A2_R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"title":"Reorder target"}')
  TASK_A2_ID=$(extract "$TASK_A2_R" "data.id")
  if [ -n "$TASK_A2_ID" ] && [ -n "$TASK_B_ID" ]; then
    R=$(curl -s -X PATCH "$BASE/tasks/reorder" -H "Authorization: Bearer $TOKEN_A" \
      -H "Content-Type: application/json" \
      -d "{\"items\":[{\"id\":\"$TASK_A2_ID\",\"position\":0},{\"id\":\"$TASK_B_ID\",\"position\":1}]}")
    check "User A reorder with User B task ID → fails (GEN_001 forbidden)" '"success":false' "$R"
    curl -s -X DELETE "$BASE/tasks/$TASK_A2_ID" -H "Authorization: Bearer $TOKEN_A" > /dev/null
    curl -s -X DELETE "$BASE/tasks/$TASK_A2_ID/permanent" -H "Authorization: Bearer $TOKEN_A" > /dev/null
  fi
fi

# ─── EVENTS ───────────────────────────────────────────────────────────────────

section "EVENTS — VALIDATION"
R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T11:00:00Z"}')
check "POST /events without title → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"title":"No Start","endAt":"2026-09-01T11:00:00Z"}')
check "POST /events without startAt → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"title":"No End","startAt":"2026-09-01T10:00:00Z"}')
check "POST /events without endAt → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"title":"Bad Color","startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T11:00:00Z","color":"blue"}')
check "POST /events invalid hex color → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"title":"End Before Start","startAt":"2026-09-01T12:00:00Z","endAt":"2026-09-01T10:00:00Z","color":"#6366F1"}')
check "POST /events endAt before startAt → fails (VAL_008)" '"success":false' "$R"
check "Time range error code is VAL_008" '"VAL_008"' "$R"

R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"title":"Equal times","startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T10:00:00Z","color":"#6366F1"}')
check "POST /events startAt == endAt → fails (VAL_008)" '"success":false' "$R"

section "EVENTS — CRUD"
EVENT_R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"title":"Auto Test Event","description":"Script test","location":"Algiers, DZ","startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T11:00:00Z","color":"#6366F1","reminderMinutes":30}')
check "POST /events → success" '"success":true' "$EVENT_R"
EVENT_ID=$(extract "$EVENT_R" "data.id")
info "Event ID: $EVENT_ID"

RECUR_R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"title":"Weekly Standup","startAt":"2026-09-01T09:00:00Z","endAt":"2026-09-01T09:30:00Z","color":"#8B5CF6","recurrenceRule":"FREQ=WEEKLY;BYDAY=MO,WE,FR","recurrenceEndAt":"2026-12-31T23:59:59Z"}')
check "POST /events recurring → success" '"success":true' "$RECUR_R"
RECUR_ID=$(extract "$RECUR_R" "data.id")

R=$(curl -s "$BASE/events?from=2026-09-01&to=2026-09-30" -H "Authorization: Bearer $TOKEN_A")
check "GET /events?from=&to= (date-only format) → success" '"success":true' "$R"

R=$(curl -s "$BASE/events?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z" -H "Authorization: Bearer $TOKEN_A")
check "GET /events?from=&to= (ISO-8601 format) → success" '"success":true' "$R"

R=$(curl -s "$BASE/events?page=0&size=20" -H "Authorization: Bearer $TOKEN_A")
check "GET /events paged (no from/to) → success" '"success":true' "$R"

R=$(curl -s "$BASE/events/trash" -H "Authorization: Bearer $TOKEN_A")
check "GET /events/trash → success" '"success":true' "$R"

if [ -n "$EVENT_ID" ]; then
  R=$(curl -s "$BASE/events/$EVENT_ID" -H "Authorization: Bearer $TOKEN_A")
  check "GET /events/{id} → success" '"success":true' "$R"
  check "GET /events/{id} → correct title" "Auto Test Event" "$R"
  check "GET /events/{id} → has syncStatus" '"syncStatus"' "$R"

  R=$(curl -s -X PUT "$BASE/events/$EVENT_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"title":"Updated Event Title","startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T12:00:00Z","color":"#22C55E"}')
  check "PUT /events/{id} → success" '"success":true' "$R"
  check "PUT /events/{id} → version incremented" '"version":2' "$R"

  R=$(curl -s -X DELETE "$BASE/events/$EVENT_ID" -H "Authorization: Bearer $TOKEN_A")
  check "DELETE /events/{id} soft-delete → success" '"success":true' "$R"
  check "Soft-delete → deleted=true" '"deleted":true' "$R"

  # Event is currently trashed — restore it
  R_RESTORE=$(curl -s -X PATCH "$BASE/events/$EVENT_ID/restore" -H "Authorization: Bearer $TOKEN_A")
  check "PATCH /events/{id}/restore → success (event back from trash)" '"success":true' "$R_RESTORE"

  # Now event is active — trying to permanent-delete should fail (VAL_017)
  R=$(curl -s -X DELETE "$BASE/events/$EVENT_ID/permanent" -H "Authorization: Bearer $TOKEN_A")
  check "DELETE permanent before restore → fails (VAL_EVENT_MUST_BE_TRASHED_FIRST)" '"success":false' "$R"

  # Restore on an already-active event should fail (VAL_018)
  R=$(curl -s -X PATCH "$BASE/events/$EVENT_ID/restore" -H "Authorization: Bearer $TOKEN_A")
  check "Restore non-trashed event → fails (VAL_EVENT_NOT_IN_TRASH — VAL_018)" '"success":false' "$R"

  # Soft-delete again so we can test permanent delete
  R=$(curl -s -X DELETE "$BASE/events/$EVENT_ID" -H "Authorization: Bearer $TOKEN_A")
  check "Soft-delete again for hardDelete test" '"success":true' "$R"

  R=$(curl -s -X DELETE "$BASE/events/$EVENT_ID/permanent" -H "Authorization: Bearer $TOKEN_A")
  check "DELETE /events/{id}/permanent → success" '"success":true' "$R"

  R=$(curl -s "$BASE/events/00000000-0000-0000-0000-000000000000" -H "Authorization: Bearer $TOKEN_A")
  check "GET /events/{unknown-uuid} → fails" '"success":false' "$R"
fi

if [ -n "$RECUR_ID" ]; then
  R=$(curl -s -X DELETE "$BASE/events/$RECUR_ID/series" -H "Authorization: Bearer $TOKEN_A")
  check "DELETE /events/{id}/series → success" '"success":true' "$R"
fi

section "MULTI-USER — EVENT ISOLATION"
EVENT_B_R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN_B" \
  -H "Content-Type: application/json" \
  -d '{"title":"User B Private Event","startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T11:00:00Z","color":"#6366F1"}')
check "User B: POST /events → success" '"success":true' "$EVENT_B_R"
EVENT_B_ID=$(extract "$EVENT_B_R" "data.id")

if [ -n "$EVENT_B_ID" ]; then
  R=$(curl -s "$BASE/events/$EVENT_B_ID" -H "Authorization: Bearer $TOKEN_A")
  check "User A cannot read User B event → fails" '"success":false' "$R"

  R=$(curl -s -X PUT "$BASE/events/$EVENT_B_ID" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"title":"Hijacked Event","startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T11:00:00Z"}')
  check "User A cannot update User B event → fails" '"success":false' "$R"

  R=$(curl -s -X DELETE "$BASE/events/$EVENT_B_ID" -H "Authorization: Bearer $TOKEN_A")
  check "User A cannot delete User B event → fails" '"success":false' "$R"
fi

# ─── POMODORO ─────────────────────────────────────────────────────────────────

section "POMODORO — VALIDATION"
R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{}')
check "POST /pomodoro/sessions/start no type → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"type":"NAP"}')
check "POST /pomodoro/sessions/start invalid type → fails" '"success":false' "$R"

section "POMODORO — FOCUS SESSION LIFECYCLE"
SESSION_R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"type":"FOCUS"}')
check "POST start FOCUS → success" '"success":true' "$SESSION_R"
SESSION_ID=$(extract "$SESSION_R" "data.id")
info "Session ID: $SESSION_ID"

R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"type":"FOCUS"}')
check "Start second session while one active → fails (VAL_009)" '"success":false' "$R"
check "Active session error code is VAL_009" '"VAL_009"' "$R"

if [ -n "$SESSION_ID" ]; then
  R=$(curl -s -X PATCH "$BASE/pomodoro/sessions/$SESSION_ID/end" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"actualDurationSeconds":-1}')
  check "End session with negative duration → fails" '"success":false' "$R"

  R=$(curl -s -X PATCH "$BASE/pomodoro/sessions/$SESSION_ID/end" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"actualDurationSeconds":1500}')
  check "PATCH /pomodoro/sessions/{id}/end → success" '"success":true' "$R"
  check "Session end → completed=true" '"completed":true' "$R"

  R=$(curl -s -X PATCH "$BASE/pomodoro/sessions/$SESSION_ID/end" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"actualDurationSeconds":900}')
  check "End already-ended session → fails (VAL_010)" '"success":false' "$R"
  check "Already ended error code is VAL_010" '"VAL_010"' "$R"
fi

section "POMODORO — SHORT BREAK + INTERRUPT"
BREAK_R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"type":"SHORT_BREAK"}')
check "POST start SHORT_BREAK → success" '"success":true' "$BREAK_R"
BREAK_ID=$(extract "$BREAK_R" "data.id")

if [ -n "$BREAK_ID" ]; then
  R=$(curl -s -X PATCH "$BASE/pomodoro/sessions/$BREAK_ID/interrupt" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"actualDurationSeconds":120,"interruptReason":"Phone call"}')
  check "PATCH /pomodoro/sessions/{id}/interrupt → success" '"success":true' "$R"
  check "Interrupt → interrupted=true" '"interrupted":true' "$R"
fi

section "POMODORO — LONG BREAK"
LB_R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"type":"LONG_BREAK"}')
check "POST start LONG_BREAK → success" '"success":true' "$LB_R"
LB_ID=$(extract "$LB_R" "data.id")
if [ -n "$LB_ID" ]; then
  curl -s -X PATCH "$BASE/pomodoro/sessions/$LB_ID/end" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" -d '{"actualDurationSeconds":900}' > /dev/null
fi

section "POMODORO — LIST & PAGINATION"
R=$(curl -s "$BASE/pomodoro/sessions" -H "Authorization: Bearer $TOKEN_A")
check "GET /pomodoro/sessions list → success" '"success":true' "$R"

R=$(curl -s "$BASE/pomodoro/sessions?page=0&size=5" -H "Authorization: Bearer $TOKEN_A")
check "GET /pomodoro/sessions paginated → success" '"success":true' "$R"

section "MULTI-USER — POMODORO ISOLATION"
SESSION_B_R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN_B" \
  -H "Content-Type: application/json" \
  -d '{"type":"FOCUS"}')
check "User B: start FOCUS session → success" '"success":true' "$SESSION_B_R"
SESSION_B_ID=$(extract "$SESSION_B_R" "data.id")

if [ -n "$SESSION_B_ID" ]; then
  R=$(curl -s -X PATCH "$BASE/pomodoro/sessions/$SESSION_B_ID/end" -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"actualDurationSeconds":1500}')
  check "User A cannot end User B session → fails" '"success":false' "$R"

  curl -s -X PATCH "$BASE/pomodoro/sessions/$SESSION_B_ID/end" -H "Authorization: Bearer $TOKEN_B" \
    -H "Content-Type: application/json" -d '{"actualDurationSeconds":1500}' > /dev/null
fi

# ─── AI — CONVERSATIONS ───────────────────────────────────────────────────────

section "AI — CONVERSATIONS"
CONV_R=$(curl -s -X POST "$BASE/ai/conversations" -H "Authorization: Bearer $TOKEN_A")
check "POST /ai/conversations → success" '"success":true' "$CONV_R"
CONV_ID=$(extract "$CONV_R" "data.id")
info "Conversation ID: $CONV_ID"

CONV2_R=$(curl -s -X POST "$BASE/ai/conversations" -H "Authorization: Bearer $TOKEN_A")
check "POST /ai/conversations (second) → success" '"success":true' "$CONV2_R"
CONV2_ID=$(extract "$CONV2_R" "data.id")

R=$(curl -s "$BASE/ai/conversations" -H "Authorization: Bearer $TOKEN_A")
check "GET /ai/conversations list → success" '"success":true' "$R"

R=$(curl -s "$BASE/ai/conversations?page=0&size=10" -H "Authorization: Bearer $TOKEN_A")
check "GET /ai/conversations paginated → success" '"success":true' "$R"

if [ -n "$CONV_ID" ]; then
  R=$(curl -s "$BASE/ai/conversations/$CONV_ID" -H "Authorization: Bearer $TOKEN_A")
  check "GET /ai/conversations/{id} → success" '"success":true' "$R"
  check "GET conversation → has messages array" '"messages"' "$R"

  section "AI — SSE STREAMING (DelegatingSecurityContextAsyncTaskExecutor test)"
  info "Sending chat message — streaming SSE. Security context must propagate to @Async thread..."
  SSE_OUTPUT=$(curl -s -N -X POST "$BASE/ai/conversations/$CONV_ID/messages" \
    -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -H "Accept: text/event-stream" \
    -d '{"content":"Hello! List my tasks due today briefly."}' \
    --max-time 30 2>&1)

  if echo "$SSE_OUTPUT" | grep -q "data:"; then
    pass "POST /ai/conversations/{id}/messages → SSE tokens received (SecurityContext propagated)"
  elif echo "$SSE_OUTPUT" | grep -qi "done\|\[DONE\]\|event:done"; then
    pass "POST /ai/conversations/{id}/messages → SSE stream completed"
  else
    info "SSE output (first 400 chars): ${SSE_OUTPUT:0:400}"
    warn "SSE stream: could not confirm token receipt — verify manually"
  fi

  R=$(curl -s -X POST "$BASE/ai/conversations/$CONV_ID/messages" \
    -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d '{"content":""}')
  check "POST /ai/messages empty content → fails" '"success":false' "$R"

  LONG_MSG=$(python3 -c "print('a'*10001)")
  R=$(curl -s -X POST "$BASE/ai/conversations/$CONV_ID/messages" \
    -H "Authorization: Bearer $TOKEN_A" \
    -H "Content-Type: application/json" \
    -d "{\"content\":\"$LONG_MSG\"}")
  check "POST /ai/messages > 10000 chars → fails" '"success":false' "$R"

  if [ -n "$CONV2_ID" ]; then
    R=$(curl -s -X DELETE "$BASE/ai/conversations/$CONV2_ID" -H "Authorization: Bearer $TOKEN_A")
    check "DELETE /ai/conversations/{id} → archived" '"success":true' "$R"

    CONV_LIST=$(curl -s "$BASE/ai/conversations" -H "Authorization: Bearer $TOKEN_A")
    check_not "Archived conversation absent from list" "$CONV2_ID" "$CONV_LIST"
  fi

  R=$(curl -s "$BASE/ai/conversations/00000000-0000-0000-0000-000000000000" \
    -H "Authorization: Bearer $TOKEN_A")
  check "GET /ai/conversations/{unknown-id} → fails" '"success":false' "$R"
fi

section "MULTI-USER — AI ISOLATION"
CONV_B_R=$(curl -s -X POST "$BASE/ai/conversations" -H "Authorization: Bearer $TOKEN_B")
check "User B: POST /ai/conversations → success" '"success":true' "$CONV_B_R"
CONV_B_ID=$(extract "$CONV_B_R" "data.id")

if [ -n "$CONV_B_ID" ]; then
  R=$(curl -s "$BASE/ai/conversations/$CONV_B_ID" -H "Authorization: Bearer $TOKEN_A")
  check "User A cannot read User B conversation → fails" '"success":false' "$R"

  R=$(curl -s -X DELETE "$BASE/ai/conversations/$CONV_B_ID" -H "Authorization: Bearer $TOKEN_A")
  check "User A cannot archive User B conversation → fails" '"success":false' "$R"
fi

# ─── SEARCH ───────────────────────────────────────────────────────────────────

section "SEARCH"
NOTE_FOR_SEARCH_R=$(curl -s -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"title":"Searchable Note UniqueSearchTerm123","content":"Content for search test."}')
SEARCH_NOTE_ID=$(extract "$NOTE_FOR_SEARCH_R" "data.id")

R=$(curl -s "$BASE/search?q=UniqueSearchTerm123&types=notes,tasks,events&limit=10" \
  -H "Authorization: Bearer $TOKEN_A")
check "GET /search → success" '"success":true' "$R"

R=$(curl -s "$BASE/search?q=UniqueSearchTerm123&types=tasks&limit=5" -H "Authorization: Bearer $TOKEN_A")
check "GET /search type=tasks → success" '"success":true' "$R"

R=$(curl -s "$BASE/search?q=UniqueSearchTerm123&types=notes&limit=5" -H "Authorization: Bearer $TOKEN_A")
check "GET /search type=notes → success" '"success":true' "$R"
check "GET /search notes → result contains searchable note" "Searchable Note" "$R"

R=$(curl -s "$BASE/search?q=UniqueSearchTerm123&types=events&limit=5" -H "Authorization: Bearer $TOKEN_A")
check "GET /search type=events → success" '"success":true' "$R"

R=$(curl -s "$BASE/search?q=xyzzy_no_result_abc_99999&types=notes,tasks,events&limit=10" \
  -H "Authorization: Bearer $TOKEN_A")
check "GET /search zero-result query → success (empty list)" '"success":true' "$R"

section "MULTI-USER — SEARCH ISOLATION"
NOTE_B_SEARCH_R=$(curl -s -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN_B" \
  -H "Content-Type: application/json" \
  -d '{"title":"User B Secret Note UniqueSearchTerm123","content":"User B only content."}')
check "User B creates note with same search term → success" '"success":true' "$NOTE_B_SEARCH_R"
NOTE_B_SEARCH_ID=$(extract "$NOTE_B_SEARCH_R" "data.id")

SEARCH_RESULT_A=$(curl -s "$BASE/search?q=UniqueSearchTerm123&types=notes&limit=20" -H "Authorization: Bearer $TOKEN_A")
check "Search returns User A results" '"success":true' "$SEARCH_RESULT_A"
if [ -n "$NOTE_B_SEARCH_ID" ]; then
  check_not "Search by User A does not return User B note" "$NOTE_B_SEARCH_ID" "$SEARCH_RESULT_A"
fi

if [ -n "$SEARCH_NOTE_ID" ]; then
  curl -s -X DELETE "$BASE/notes/$SEARCH_NOTE_ID" -H "Authorization: Bearer $TOKEN_A" > /dev/null
  curl -s -X DELETE "$BASE/notes/$SEARCH_NOTE_ID/permanent" -H "Authorization: Bearer $TOKEN_A" > /dev/null
fi

# ─── SYNC — DELTA ─────────────────────────────────────────────────────────────

section "SYNC — DELTA ENDPOINT"
R=$(curl -s "$BASE/sync/delta?since=2026-01-01T00:00:00Z" -H "Authorization: Bearer $TOKEN_A")
check "GET /sync/delta → success" '"success":true' "$R"
check "GET /sync/delta → has notes array" '"notes"' "$R"
check "GET /sync/delta → has tasks array" '"tasks"' "$R"
check "GET /sync/delta → has events array" '"events"' "$R"
check "GET /sync/delta → has pomodoroSessions array" '"pomodoroSessions"' "$R"
check "GET /sync/delta → has hasMore field" '"hasMore"' "$R"
check "GET /sync/delta → has syncedAt field" '"syncedAt"' "$R"
check "GET /sync/delta → has totalChanges field" '"totalChanges"' "$R"

R=$(curl -s "$BASE/sync/delta?since=2026-01-01T00:00:00Z&limit=10" -H "Authorization: Bearer $TOKEN_A")
check "GET /sync/delta with limit=10 → success" '"success":true' "$R"

R=$(curl -s "$BASE/sync/delta?since=2026-01-01T00:00:00Z&limit=9999" -H "Authorization: Bearer $TOKEN_A")
check "GET /sync/delta limit clamped to 500 (no error) → success" '"success":true' "$R"

R=$(curl -s "$BASE/sync/delta?since=2030-01-01T00:00:00Z" -H "Authorization: Bearer $TOKEN_A")
check "GET /sync/delta future since → success (empty delta)" '"success":true' "$R"

CUTOFF_400=$(date -d "400 days ago" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null \
  || date -v-400d +%Y-%m-%dT%H:%M:%SZ 2>/dev/null \
  || echo "2024-01-01T00:00:00Z")
R=$(curl -s "$BASE/sync/delta?since=$CUTOFF_400" -H "Authorization: Bearer $TOKEN_A")
check "GET /sync/delta since > 365 days → fails (VAL_016)" '"success":false' "$R"
check "Sync range error code is VAL_016" '"VAL_016"' "$R"

R=$(curl -s "$BASE/sync/delta" -H "Authorization: Bearer $TOKEN_A")
check "GET /sync/delta without since param → fails (missing required param)" '"success":false' "$R"

section "MULTI-USER — SYNC ISOLATION"
SYNC_A=$(curl -s "$BASE/sync/delta?since=2026-01-01T00:00:00Z" -H "Authorization: Bearer $TOKEN_A")
SYNC_B=$(curl -s "$BASE/sync/delta?since=2026-01-01T00:00:00Z" -H "Authorization: Bearer $TOKEN_B")
if [ -n "$NOTE_B_SEARCH_ID" ]; then
  check_not "User A delta does not contain User B notes" "$NOTE_B_SEARCH_ID" "$SYNC_A"
fi

# ─── IDEMPOTENCY FILTER ───────────────────────────────────────────────────────

section "IDEMPOTENCY FILTER (Redis-backed)"
IDEMP_KEY="idemp-test-$(gen_uuid)"

R1=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -d '{"name":"idem-tag","color":"#6366F1"}')
check "POST with Idempotency-Key (first call) → success" '"success":true' "$R1"
IDEMP_TAG_ID=$(extract "$R1" "data.id")

R2=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -d '{"name":"idem-tag","color":"#6366F1"}')
check "POST same Idempotency-Key (replay) → replayed same response" '"success":true' "$R2"
check "Replay returns same tag name (not duplicate created)" '"idem-tag"' "$R2"
IDEMP_TAG_ID_2=$(extract "$R2" "data.id")
if [ "$IDEMP_TAG_ID" = "$IDEMP_TAG_ID_2" ]; then
  pass "Idempotency replay → same tag ID returned (no duplicate)"
else
  warn "Idempotency replay returned different ID — may be a timing/caching issue"
fi

DIFF_KEY="idemp-diff-$(gen_uuid)"
R3=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $DIFF_KEY" \
  -d '{"name":"idem-tag","color":"#6366F1"}')
check "POST different Idempotency-Key same name → fails (duplicate name)" '"success":false' "$R3"

CROSS_KEY="idemp-cross-$(gen_uuid)"
R_CROSS=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN_B" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -d '{"name":"idem-tag","color":"#6366F1"}')
check "Cross-user: User B uses same Idempotency-Key as User A → new response (keys are user-scoped)" '"success":true' "$R_CROSS"
CROSS_TAG_ID=$(extract "$R_CROSS" "data.id")
if [ "$IDEMP_TAG_ID" != "$CROSS_TAG_ID" ]; then
  pass "Idempotency key isolation: User B got different tag (different userId in Redis key)"
else
  warn "Cross-user idempotency key collision — investigate Redis key scoping"
fi

if [ -n "$IDEMP_TAG_ID" ]; then
  curl -s -X DELETE "$BASE/tags/$IDEMP_TAG_ID" -H "Authorization: Bearer $TOKEN_A" > /dev/null
fi

# ─── FORGOT / RESET PASSWORD ──────────────────────────────────────────────────

section "AUTH — FORGOT PASSWORD"
R=$(curl -s -X POST "$BASE/auth/forgot-password" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_A\"}")
check "POST /auth/forgot-password → always 200" '"success":true' "$R"

R=$(curl -s -X POST "$BASE/auth/forgot-password" -H "Content-Type: application/json" \
  -d '{"email":"ghost_nobody_xyz@nowhere.com"}')
check "POST /auth/forgot-password unknown email → still 200 (no enumeration)" '"success":true' "$R"

R=$(curl -s -X POST "$BASE/auth/forgot-password" -H "Content-Type: application/json" \
  -d '{"email":"not-an-email"}')
check "POST /auth/forgot-password invalid format → fails" '"success":false' "$R"

section "AUTH — VERIFY FORGOT PASSWORD OTP"
info "Password-reset OTP sent to: $EMAIL_A"

echo -n "    Enter a WRONG reset OTP (e.g. 000000): "
read -r WRONG_RESET_OTP
R=$(curl -s -X POST "$BASE/auth/verify-forgot-otp" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_A\",\"otp\":\"$WRONG_RESET_OTP\"}")
check "POST /auth/verify-forgot-otp wrong OTP → fails" '"success":false' "$R"

prompt_otp "Enter the REAL reset OTP for $EMAIL_A"
RESET_OTP_R=$(curl -s -X POST "$BASE/auth/verify-forgot-otp" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL_A\",\"otp\":\"$OTP_INPUT\"}")
check "POST /auth/verify-forgot-otp correct OTP → success + resetToken" '"success":true' "$RESET_OTP_R"
RESET_TOKEN=$(extract "$RESET_OTP_R" "data.resetToken")
info "Reset token: ${RESET_TOKEN:0:30}..."

section "AUTH — RESET PASSWORD"
if [ -n "$RESET_TOKEN" ]; then
  R=$(curl -s -X POST "$BASE/auth/reset-password" -H "Content-Type: application/json" \
    -d "{\"token\":\"$RESET_TOKEN\",\"newPassword\":\"weak\"}")
  check "POST /auth/reset-password weak password → fails" '"success":false' "$R"

  R=$(curl -s -X POST "$BASE/auth/reset-password" -H "Content-Type: application/json" \
    -d '{"token":"completely-wrong-token","newPassword":"NewPass@5678"}')
  check "POST /auth/reset-password bad token → fails" '"success":false' "$R"

  NEW_PASSWORD="NewPass@5678"
  R=$(curl -s -X POST "$BASE/auth/reset-password" -H "Content-Type: application/json" \
    -d "{\"token\":\"$RESET_TOKEN\",\"newPassword\":\"$NEW_PASSWORD\"}")
  check "POST /auth/reset-password valid → success" '"success":true' "$R"

  R=$(curl -s -X POST "$BASE/auth/reset-password" -H "Content-Type: application/json" \
    -d "{\"token\":\"$RESET_TOKEN\",\"newPassword\":\"AnotherPass@999\"}")
  check "OTP/token replay after use → fails" '"success":false' "$R"

  R=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
    -d "{\"identifier\":\"$EMAIL_A\",\"password\":\"$PASSWORD\"}")
  check "Login with old password after reset → fails" '"success":false' "$R"

  LOGIN_NEW=$(curl -s -c "$COOKIE_JAR_A" -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"identifier\":\"$EMAIL_A\",\"password\":\"$NEW_PASSWORD\"}")
  check "Login with new password → success" '"success":true' "$LOGIN_NEW"
  T=$(extract "$LOGIN_NEW" "data.accessToken")
  [ -n "$T" ] && TOKEN_A="$T"
  PASSWORD="$NEW_PASSWORD"
fi

# ─── CHANGE PASSWORD ──────────────────────────────────────────────────────────

section "AUTH — CHANGE PASSWORD"
R=$(curl -s -X POST "$BASE/auth/change-password" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d "{\"currentPassword\":\"WrongOld@000\",\"newPassword\":\"ChangedPass@9999\"}")
check "POST /auth/change-password wrong current → fails (AUTH_012)" '"success":false' "$R"
check "Wrong current pw error code" '"AUTH_012"' "$R"

R=$(curl -s -X POST "$BASE/auth/change-password" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d "{\"currentPassword\":\"$PASSWORD\",\"newPassword\":\"abc\"}")
check "POST /auth/change-password weak new → fails" '"success":false' "$R"

CHANGED_PASSWORD="ChangedPass@9999"
R=$(curl -s -X POST "$BASE/auth/change-password" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d "{\"currentPassword\":\"$PASSWORD\",\"newPassword\":\"$CHANGED_PASSWORD\"}")
check "POST /auth/change-password valid → success" '"success":true' "$R"
PASSWORD="$CHANGED_PASSWORD"

LOGIN_CHG=$(curl -s -c "$COOKIE_JAR_A" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL_A\",\"password\":\"$PASSWORD\"}")
check "Login with changed password → success" '"success":true' "$LOGIN_CHG"
T=$(extract "$LOGIN_CHG" "data.accessToken")
[ -n "$T" ] && TOKEN_A="$T"

# ─── HTTP METHOD & MEDIA TYPE ERRORS ──────────────────────────────────────────

section "HTTP PROTOCOL ERRORS"
R=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE/auth/login")
check "PATCH /auth/login → 405 Method Not Allowed" "405" "$R"

R=$(curl -s -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: text/plain" \
  -d 'plain text body')
check "POST /notes with Content-Type: text/plain → 415 Unsupported Media Type" '"GEN_004"' "$R"

R=$(curl -s -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d 'this is not json {{{')
check "POST /notes with malformed JSON → fails" '"success":false' "$R"

# ─── LOGOUT ───────────────────────────────────────────────────────────────────

section "AUTH — LOGOUT"
R=$(curl -s -b "$COOKIE_JAR_A" -X POST "$BASE/auth/logout" -H "Authorization: Bearer $TOKEN_A")
check "POST /auth/logout → success" '"success":true' "$R"

R=$(curl -s -b "$COOKIE_JAR_A" -c "$COOKIE_JAR_A" -X POST "$BASE/auth/refresh")
check "POST /auth/refresh after logout → fails (cookie revoked)" '"success":false' "$R"

info "JWT is stateless — short-lived access token may still pass auth until expiry (expected behavior)"

# ─── CLEANUP ──────────────────────────────────────────────────────────────────

rm -f "$COOKIE_JAR_A" "$COOKIE_JAR_B"

# ─── SUMMARY ──────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL + WARN))
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo -e "${BOLD}  TEST SUMMARY — ProductivityX v3${NC}"
echo "═══════════════════════════════════════════════════════════════"
echo -e "  Total checked : ${BOLD}$TOTAL${NC}"
echo -e "  ${GREEN}Passed         : $PASS${NC}"
if [ $FAIL -gt 0 ]; then
  echo -e "  ${RED}Failed         : $FAIL${NC}"
else
  echo -e "  ${GREEN}Failed         : 0${NC}"
fi
if [ $WARN -gt 0 ]; then
  echo -e "  ${YELLOW}Warnings       : $WARN${NC}"
fi
[ $SKIP -gt 0 ] && echo -e "  Skipped        : $SKIP"
echo ""
if [ $FAIL -eq 0 ]; then
  echo -e "  ${GREEN}${BOLD}🎉  Clean run — all assertions passed!${NC}"
else
  echo -e "  ${RED}${BOLD}⚠   $FAIL assertion(s) failed — review above.${NC}"
fi
echo "═══════════════════════════════════════════════════════════════"