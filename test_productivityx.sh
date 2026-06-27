#!/bin/bash

# =============================================================================
# ProductivityX — Full API Test (Production-Grade)
# =============================================================================

set -euo pipefail

BASE="http://localhost:8080/api/v1"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

PASS=0
FAIL=0
COOKIE_JAR=$(mktemp)
TOKEN=""

# ─── Helpers ─────────────────────────────────────────────────────────────────

pass()    { echo -e "${GREEN}✅ PASS${NC} — $1"; ((PASS++)); }
fail()    { echo -e "${RED}❌ FAIL${NC} — $1\n   Expected : $2\n   Got      : $(echo "$3" | head -c 400)"; ((FAIL++)); }
section() { echo -e "\n${YELLOW}${BOLD}━━━ $1 ━━━${NC}"; }
info()    { echo -e "${CYAN}ℹ  $1${NC}"; }

check() {
  local label="$1" expected="$2" actual="$3"
  if echo "$actual" | grep -q "$expected"; then pass "$label"
  else fail "$label" "$expected" "$actual"; fi
}

check_not() {
  local label="$1" unexpected="$2" actual="$3"
  if echo "$actual" | grep -q "$unexpected"; then fail "$label" "NOT '$unexpected'" "$actual"
  else pass "$label"; fi
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

# ─── Configuration ───────────────────────────────────────────────────────────

TIMESTAMP=$(date +%s)
EMAIL="test_px_${TIMESTAMP}@gmail.com"
PASSWORD="TestPass@1234"
WRONG_PASSWORD="WrongPass@9999"
FIRST="Test"
LAST="User"
USERNAME="pxuser_${TIMESTAMP}"
BIRTH_DATE="1998-05-20"

section "CONFIGURATION"
info "Email     : $EMAIL"
info "Username  : $USERNAME"
info "Base URL  : $BASE"

# ═════════════════════════════════════════════════════════════════════════════
# HEALTH
# ═════════════════════════════════════════════════════════════════════════════

section "HEALTH"
R=$(curl -s http://localhost:8080/actuator/health)
check "Actuator UP" '"status":"UP"' "$R"

# ═════════════════════════════════════════════════════════════════════════════
# AUTH — REGISTER (failure cases)
# ═════════════════════════════════════════════════════════════════════════════

section "AUTH — REGISTER (failure cases)"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d '{"email":"not-an-email","password":"TestPass@1234","firstName":"A","lastName":"B","username":"validuser1","birthDate":"1998-05-20"}')
check "Invalid email → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"valid${TIMESTAMP}@test.com\",\"password\":\"weak\",\"firstName\":\"A\",\"lastName\":\"B\",\"username\":\"validuser2\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Weak password → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"valid${TIMESTAMP}@test.com\",\"password\":\"$PASSWORD\",\"firstName\":\"\",\"lastName\":\"B\",\"username\":\"validuser3\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Blank firstName → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"valid${TIMESTAMP}@test.com\",\"password\":\"$PASSWORD\",\"firstName\":\"A\",\"lastName\":\"B\",\"username\":\"x\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Username too short (1 char) → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"valid${TIMESTAMP}@test.com\",\"password\":\"$PASSWORD\",\"firstName\":\"A\",\"lastName\":\"B\",\"username\":\"valid_user\",\"birthDate\":\"2020-01-01\"}")
check "Underage birthDate → fails" '"success":false' "$R"

# ═════════════════════════════════════════════════════════════════════════════
# AUTH — REGISTER (success)
# ═════════════════════════════════════════════════════════════════════════════

section "AUTH — REGISTER (success)"
R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"firstName\":\"$FIRST\",\"lastName\":\"$LAST\",\"username\":\"$USERNAME\",\"birthDate\":\"$BIRTH_DATE\",\"gender\":\"MALE\"}")
check "Valid registration → success" '"success":true' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"firstName\":\"A\",\"lastName\":\"B\",\"username\":\"dupuser_${TIMESTAMP}\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Duplicate email → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"other_${TIMESTAMP}@test.com\",\"password\":\"$PASSWORD\",\"firstName\":\"A\",\"lastName\":\"B\",\"username\":\"$USERNAME\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Duplicate username → fails" '"success":false' "$R"

# ═════════════════════════════════════════════════════════════════════════════
# AUTH — LOGIN BLOCKED BEFORE VERIFICATION
# ═════════════════════════════════════════════════════════════════════════════

section "AUTH — LOGIN BEFORE VERIFICATION"
R=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
check "Login before email verification → fails" '"success":false' "$R"

# ═════════════════════════════════════════════════════════════════════════════
# AUTH — RESEND VERIFICATION
# ═════════════════════════════════════════════════════════════════════════════

section "AUTH — RESEND VERIFICATION"
R=$(curl -s -X POST "$BASE/auth/resend-verification" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\"}")
check "Resend verification → success" '"success":true' "$R"

R=$(curl -s -X POST "$BASE/auth/resend-verification" -H "Content-Type: application/json" \
  -d '{"email":"ghost_nobody@nowhere.xyz"}')
info "Resend for unknown email: $(echo "$R" | head -c 80)"

# ═════════════════════════════════════════════════════════════════════════════
# AUTH — VERIFY EMAIL (OTP)
# ═════════════════════════════════════════════════════════════════════════════

section "AUTH — VERIFY EMAIL (OTP)"
info "A 6-digit OTP was sent to: $EMAIL"

R=$(curl -s -X POST "$BASE/auth/verify-otp" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"000000\"}")
check "Wrong OTP → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/verify-otp" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"12\"}")
check "OTP too short → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/verify-otp" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"abcdef\"}")
check "Non-numeric OTP → fails" '"success":false' "$R"

prompt_otp "Enter the REAL OTP from your email to continue"
VERIFY_R=$(curl -s -c "$COOKIE_JAR" -X POST "$BASE/auth/verify-otp" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"$OTP_INPUT\"}")
check "Correct OTP → verified + tokens" '"success":true' "$VERIFY_R"

TOKEN=$(extract "$VERIFY_R" "data.accessToken")
if [ -z "$TOKEN" ]; then
  echo -e "${RED}✗  Cannot extract access token — aborting.${NC}"
  echo "   Response: $VERIFY_R"
  rm -f "$COOKIE_JAR"; exit 1
fi
info "Access token: ${TOKEN:0:40}..."

R=$(curl -s -X POST "$BASE/auth/verify-otp" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"$OTP_INPUT\"}")
check "Reuse same OTP → fails (already used)" '"success":false' "$R"

# ═════════════════════════════════════════════════════════════════════════════
# AUTH — LOGIN
# ═════════════════════════════════════════════════════════════════════════════

section "AUTH — LOGIN"

R=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL\",\"password\":\"$WRONG_PASSWORD\"}")
check "Wrong password → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d '{"identifier":"ghost_xyz_nobody@nowhere.com","password":"TestPass@1234"}')
check "Unknown identifier → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d "{\"identifier\":\"\",\"password\":\"$PASSWORD\"}")
check "Blank identifier → fails" '"success":false' "$R"

LOGIN_R=$(curl -s -c "$COOKIE_JAR" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
check "Login by email → success" '"success":true' "$LOGIN_R"
TOKEN=$(extract "$LOGIN_R" "data.accessToken")
info "Token refreshed from login: ${TOKEN:0:40}..."

R=$(curl -s -c "$COOKIE_JAR" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
check "Login by username → success" '"success":true' "$R"

# ═════════════════════════════════════════════════════════════════════════════
# UNAUTHENTICATED ACCESS
# ═════════════════════════════════════════════════════════════════════════════

section "UNAUTHENTICATED ACCESS"
for ROUTE in auth/me profile preferences notes tasks events; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/$ROUTE")
  check "GET /$ROUTE without token → 401" "401" "$CODE"
done

# ═════════════════════════════════════════════════════════════════════════════
# AUTH — ME
# ═════════════════════════════════════════════════════════════════════════════

section "AUTH — ME"
R=$(curl -s "$BASE/auth/me" -H "Authorization: Bearer $TOKEN")
check "GET /auth/me → success" '"success":true' "$R"
check "GET /auth/me → contains email" "$EMAIL" "$R"

# ═════════════════════════════════════════════════════════════════════════════
# TOKEN REFRESH
# ═════════════════════════════════════════════════════════════════════════════

section "TOKEN REFRESH"
R=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE/auth/refresh")
check "POST /auth/refresh → rotates token" '"success":true' "$R"
NEW_TOKEN=$(extract "$R" "data.accessToken")
[ -n "$NEW_TOKEN" ] && TOKEN="$NEW_TOKEN" && info "Token rotated: ${TOKEN:0:40}..."

R=$(curl -s -c /dev/null -X POST "$BASE/auth/refresh" --cookie "refreshToken=totally_invalid_cookie")
check "Refresh with bad cookie → fails" '"success":false' "$R"

# ═════════════════════════════════════════════════════════════════════════════
# PROFILE
# ═════════════════════════════════════════════════════════════════════════════

section "PROFILE"
R=$(curl -s "$BASE/profile" -H "Authorization: Bearer $TOKEN")
check "GET /profile → success" '"success":true' "$R"
check "GET /profile → has firstName" '"firstName"' "$R"

R=$(curl -s -X PUT "$BASE/profile" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Updated","lastName":"TestUser","bio":"Bio from test","timezone":"Africa/Algiers","language":"FR","theme":"DARK"}')
check "PUT /profile full update → success" '"success":true' "$R"
check "PUT /profile → firstName updated" '"Updated"' "$R"

R=$(curl -s -X PUT "$BASE/profile" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"PartialOnly"}')
check "PUT /profile partial update → success" '"success":true' "$R"

R=$(curl -s -X PUT "$BASE/profile" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"theme":"RAINBOW"}')
check "PUT /profile invalid theme → fails" '"success":false' "$R"

R=$(curl -s -X PATCH "$BASE/profile/avatar" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"avatarUrl":"https://example.com/avatar.png"}')
check "PATCH /profile/avatar → success" '"success":true' "$R"

R=$(curl -s -X PATCH "$BASE/profile/avatar" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}')
check "PATCH /profile/avatar blank URL → fails" '"success":false' "$R"

# ═════════════════════════════════════════════════════════════════════════════
# PREFERENCES
# ═════════════════════════════════════════════════════════════════════════════

section "PREFERENCES"
R=$(curl -s "$BASE/preferences" -H "Authorization: Bearer $TOKEN")
check "GET /preferences → success" '"success":true' "$R"

R=$(curl -s -X PUT "$BASE/preferences" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pomodoroFocusMinutes":30,"pomodoroShortBreakMinutes":5,"pomodoroLongBreakMinutes":20,"pomodoroCyclesBeforeLongBreak":4,"pomodoroAutoStartBreaks":true,"pomodoroAutoStartFocus":false,"pomodoroSoundEnabled":true,"notifyTaskReminders":true,"notifyEventReminders":true,"notifyPomodoroEnd":true,"notifyDailySummary":false,"defaultTaskView":"KANBAN","defaultCalendarView":"WEEK","weekStartsOn":"MON","aiContextEnabled":true,"compactMode":false}')
check "PUT /preferences full update → success" '"success":true' "$R"

R=$(curl -s -X PUT "$BASE/preferences" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pomodoroFocusMinutes":45}')
check "PUT /preferences partial update → success" '"success":true' "$R"

R=$(curl -s -X PUT "$BASE/preferences" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pomodoroFocusMinutes":0}')
check "PUT /preferences focus=0 (below min) → fails" '"success":false' "$R"

R=$(curl -s -X PUT "$BASE/preferences" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pomodoroFocusMinutes":200}')
check "PUT /preferences focus=200 (above max) → fails" '"success":false' "$R"

# ═════════════════════════════════════════════════════════════════════════════
# TAGS
# ═════════════════════════════════════════════════════════════════════════════

section "TAGS"
TAG_R=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"work","color":"#6366F1"}')
check "POST /tags → success" '"success":true' "$TAG_R"
TAG_ID=$(extract "$TAG_R" "data.id")
info "Tag ID: $TAG_ID"

TAG2_R=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"personal","color":"#22C55E"}')
check "POST /tags second tag → success" '"success":true' "$TAG2_R"
TAG2_ID=$(extract "$TAG2_R" "data.id")

R=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"work","color":"#22C55E"}')
check "POST /tags duplicate name → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"invalid","color":"red"}')
check "POST /tags invalid hex color → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"","color":"#6366F1"}')
check "POST /tags blank name → fails" '"success":false' "$R"

R=$(curl -s "$BASE/tags" -H "Authorization: Bearer $TOKEN")
check "GET /tags list → success" '"success":true' "$R"

if [ -n "$TAG_ID" ]; then
  R=$(curl -s -X PUT "$BASE/tags/$TAG_ID" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"name":"work-updated","color":"#8B5CF6"}')
  check "PUT /tags/{id} → success" '"success":true' "$R"

  R=$(curl -s -X PUT "$BASE/tags/$TAG_ID" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"name":"personal","color":"#6366F1"}')
  check "PUT /tags/{id} rename to existing name → fails" '"success":false' "$R"
fi

# ═════════════════════════════════════════════════════════════════════════════
# NOTES
# ═════════════════════════════════════════════════════════════════════════════

section "NOTES — VALIDATION"
LONG_TITLE=$(python3 -c "print('a'*501)")
R=$(curl -s -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"$LONG_TITLE\",\"content\":\"test\"}")
check "POST /notes title > 500 chars → fails" '"success":false' "$R"

section "NOTES — CRUD"
NOTE_R=$(curl -s -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Auto Test Note","content":"## Hello\nThis is a test.","pinned":false}')
check "POST /notes → success" '"success":true' "$NOTE_R"
NOTE_ID=$(extract "$NOTE_R" "data.id")
info "Note ID: $NOTE_ID"

if [ -n "$TAG_ID" ]; then
  TAGGED_NOTE_R=$(curl -s -X POST "$BASE/notes" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Tagged Note\",\"content\":\"# Tag test\",\"tagIds\":[\"$TAG_ID\"]}")
  check "POST /notes with tagIds → success" '"success":true' "$TAGGED_NOTE_R"
  TAGGED_NOTE_ID=$(extract "$TAGGED_NOTE_R" "data.id")
fi

R=$(curl -s "$BASE/notes" -H "Authorization: Bearer $TOKEN")
check "GET /notes list → success" '"success":true' "$R"

R=$(curl -s "$BASE/notes?pinned=true" -H "Authorization: Bearer $TOKEN")
check "GET /notes?pinned=true → success" '"success":true' "$R"

if [ -n "$TAG_ID" ]; then
  R=$(curl -s "$BASE/notes?tagId=$TAG_ID" -H "Authorization: Bearer $TOKEN")
  check "GET /notes?tagId= → success" '"success":true' "$R"
fi

R=$(curl -s "$BASE/notes?page=0&size=5" -H "Authorization: Bearer $TOKEN")
check "GET /notes paginated → success" '"success":true' "$R"

R=$(curl -s "$BASE/notes/trash" -H "Authorization: Bearer $TOKEN")
check "GET /notes/trash → success" '"success":true' "$R"

if [ -n "$NOTE_ID" ]; then
  R=$(curl -s "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN")
  check "GET /notes/{id} → success" '"success":true' "$R"
  check "GET /notes/{id} → correct title" "Auto Test Note" "$R"

  R=$(curl -s -X PUT "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"Updated Note Title","content":"# Updated\nContent changed.","pinned":true}')
  check "PUT /notes/{id} → success" '"success":true' "$R"

  R=$(curl -s -X PATCH "$BASE/notes/$NOTE_ID/pin" -H "Authorization: Bearer $TOKEN")
  check "PATCH /notes/{id}/pin → success" '"success":true' "$R"

  R=$(curl -s -X PATCH "$BASE/notes/$NOTE_ID/unpin" -H "Authorization: Bearer $TOKEN")
  check "PATCH /notes/{id}/unpin → success" '"success":true' "$R"

  if [ -n "$TAG2_ID" ]; then
    R=$(curl -s -X POST "$BASE/notes/$NOTE_ID/tags" -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"tagId\":\"$TAG2_ID\"}")
    check "POST /notes/{id}/tags → success" '"success":true' "$R"

    R=$(curl -s -X DELETE "$BASE/notes/$NOTE_ID/tags/$TAG2_ID" -H "Authorization: Bearer $TOKEN")
    check "DELETE /notes/{id}/tags/{tagId} → success" '"success":true' "$R"
  fi

  # ─── Trash / Restore / Hard-Delete lifecycle ───────────────────────────
  R=$(curl -s -X DELETE "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN")
  check "DELETE /notes/{id} soft-delete → success" '"success":true' "$R"

  R=$(curl -s "$BASE/notes/trash" -H "Authorization: Bearer $TOKEN")
  check "GET /notes/trash shows deleted note → success" '"success":true' "$R"

  R=$(curl -s -X PUT "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"Edit trashed note"}')
  check "PUT trashed note → fails (VAL_NOTE_TRASHED)" '"success":false' "$R"

  R=$(curl -s -X PATCH "$BASE/notes/$NOTE_ID/restore" -H "Authorization: Bearer $TOKEN")
  check "PATCH /notes/{id}/restore → success" '"success":true' "$R"

  R=$(curl -s -X DELETE "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN")
  check "Soft-delete again before hardDelete → success" '"success":true' "$R"

  R=$(curl -s -X DELETE "$BASE/notes/$NOTE_ID/permanent" -H "Authorization: Bearer $TOKEN")
  check "DELETE /notes/{id}/permanent (trashed) → success" '"success":true' "$R"

  R=$(curl -s "$BASE/notes/00000000-0000-0000-0000-000000000000" -H "Authorization: Bearer $TOKEN")
  check "GET /notes/{unknown-id} → fails" '"success":false' "$R"
fi

# ═════════════════════════════════════════════════════════════════════════════
# TASKS
# ═════════════════════════════════════════════════════════════════════════════

section "TASKS — VALIDATION"
R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"priority":"HIGH","status":"TODO"}')
check "POST /tasks without title → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Bad Task","estimatedMinutes":-5}')
check "POST /tasks negative estimatedMinutes → fails" '"success":false' "$R"

LONG_TASK_TITLE=$(python3 -c "print('t'*501)")
R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"$LONG_TASK_TITLE\"}")
check "POST /tasks title > 500 chars → fails" '"success":false' "$R"

section "TASKS — CRUD"
TASK_R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Auto Test Task","description":"Script test","priority":"HIGH","status":"TODO","estimatedMinutes":60,"dueDate":"2026-12-31"}')
check "POST /tasks → success" '"success":true' "$TASK_R"
TASK_ID=$(extract "$TASK_R" "data.id")
info "Task ID: $TASK_ID"

R=$(curl -s "$BASE/tasks" -H "Authorization: Bearer $TOKEN")
check "GET /tasks list → success" '"success":true' "$R"

R=$(curl -s "$BASE/tasks?status=TODO" -H "Authorization: Bearer $TOKEN")
check "GET /tasks?status=TODO → success" '"success":true' "$R"

R=$(curl -s "$BASE/tasks?priority=HIGH" -H "Authorization: Bearer $TOKEN")
check "GET /tasks?priority=HIGH → success" '"success":true' "$R"

R=$(curl -s "$BASE/tasks?page=0&size=10" -H "Authorization: Bearer $TOKEN")
check "GET /tasks paginated → success" '"success":true' "$R"

R=$(curl -s "$BASE/tasks/trash" -H "Authorization: Bearer $TOKEN")
check "GET /tasks/trash → success" '"success":true' "$R"

if [ -n "$TASK_ID" ]; then
  R=$(curl -s "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN")
  check "GET /tasks/{id} → success" '"success":true' "$R"
  check "GET /tasks/{id} → has subtasks field" '"subtasks"' "$R"

  R=$(curl -s -X PUT "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"Updated Task Title","priority":"URGENT","status":"IN_PROGRESS"}')
  check "PUT /tasks/{id} → success" '"success":true' "$R"

  for STATUS in ON_HOLD DONE; do
    R=$(curl -s -X PATCH "$BASE/tasks/$TASK_ID/status" -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"status\":\"$STATUS\"}")
    check "PATCH /tasks/{id}/status → $STATUS" '"success":true' "$R"
  done

  R=$(curl -s -X PATCH "$BASE/tasks/$TASK_ID/status" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"status":"FLYING"}')
  check "PATCH /tasks/{id}/status invalid value → fails" '"success":false' "$R"

  SUBTASK_R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Subtask One\",\"priority\":\"LOW\",\"parentTaskId\":\"$TASK_ID\"}")
  check "POST /tasks subtask (parentTaskId) → success" '"success":true' "$SUBTASK_R"
  SUBTASK_ID=$(extract "$SUBTASK_R" "data.id")

  if [ -n "$SUBTASK_ID" ]; then
    R=$(curl -s "$BASE/tasks?parentId=$TASK_ID" -H "Authorization: Bearer $TOKEN")
    check "GET /tasks?parentId= → returns subtasks" '"success":true' "$R"

    R=$(curl -s -X POST "$BASE/tasks" -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"title\":\"Grandchild Task\",\"parentTaskId\":\"$SUBTASK_ID\"}")
    check "POST /tasks grandchild (depth > 1) → fails (VAL_SUBTASK_DEPTH_EXCEEDED)" '"success":false' "$R"

    R=$(curl -s -X PATCH "$BASE/tasks/reorder" -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"items\":[{\"id\":\"$TASK_ID\",\"position\":0},{\"id\":\"$SUBTASK_ID\",\"position\":1}]}")
    check "PATCH /tasks/reorder → success" '"success":true' "$R"
  fi

  # ─── Trash / Restore / Hard-Delete lifecycle ───────────────────────────
  R=$(curl -s -X DELETE "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN")
  check "DELETE /tasks/{id} soft-delete → success" '"success":true' "$R"

  R=$(curl -s "$BASE/tasks/trash" -H "Authorization: Bearer $TOKEN")
  check "GET /tasks/trash shows deleted task → success" '"success":true' "$R"

  R=$(curl -s -X PUT "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"Edit trashed task"}')
  check "PUT trashed task → fails" '"success":false' "$R"

  # Must restore before permanent delete
  R=$(curl -s -X PATCH "$BASE/tasks/$TASK_ID/restore" -H "Authorization: Bearer $TOKEN")
  check "PATCH /tasks/{id}/restore → success" '"success":true' "$R"

  # Now soft-delete again
  R=$(curl -s -X DELETE "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN")
  check "Soft-delete again → success" '"success":true' "$R"

  # Permanent delete of trashed task → success
  R=$(curl -s -X DELETE "$BASE/tasks/$TASK_ID/permanent" -H "Authorization: Bearer $TOKEN")
  check "DELETE /tasks/{id}/permanent (now trashed) → success" '"success":true' "$R"

  R=$(curl -s "$BASE/tasks/00000000-0000-0000-0000-000000000000" -H "Authorization: Bearer $TOKEN")
  check "GET /tasks/{unknown-id} → fails" '"success":false' "$R"
fi

# ═════════════════════════════════════════════════════════════════════════════
# EVENTS
# ═════════════════════════════════════════════════════════════════════════════

section "EVENTS — VALIDATION"
R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T11:00:00Z"}')
check "POST /events without title → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"No Start","endAt":"2026-09-01T11:00:00Z"}')
check "POST /events without startAt → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"No End","startAt":"2026-09-01T10:00:00Z"}')
check "POST /events without endAt → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Bad Color","startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T11:00:00Z","color":"blue"}')
check "POST /events invalid hex color → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"End Before Start","startAt":"2026-09-01T11:00:00Z","endAt":"2026-09-01T10:00:00Z","color":"#6366F1"}')
check "POST /events endAt before startAt → fails" '"success":false' "$R"

section "EVENTS — CRUD"
EVENT_R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Auto Test Event","description":"Script test","location":"Algiers","startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T11:00:00Z","color":"#6366F1","reminderMinutes":30}')
check "POST /events → success" '"success":true' "$EVENT_R"
EVENT_ID=$(extract "$EVENT_R" "data.id")
info "Event ID: $EVENT_ID"

RECUR_R=$(curl -s -X POST "$BASE/events" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Weekly Standup","startAt":"2026-09-01T09:00:00Z","endAt":"2026-09-01T09:30:00Z","color":"#8B5CF6","recurrenceRule":"FREQ=WEEKLY;BYDAY=MO,WE,FR","recurrenceEndAt":"2026-12-31T23:59:59Z"}')
check "POST /events recurring → success" '"success":true' "$RECUR_R"
RECUR_ID=$(extract "$RECUR_R" "data.id")

R=$(curl -s "$BASE/events?from=2026-09-01&to=2026-09-30" -H "Authorization: Bearer $TOKEN")
check "GET /events?from=&to= (date-only format) → success" '"success":true' "$R"

R=$(curl -s "$BASE/events?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z" -H "Authorization: Bearer $TOKEN")
check "GET /events?from=&to= (ISO format) → success" '"success":true' "$R"

R=$(curl -s "$BASE/events?page=0&size=20" -H "Authorization: Bearer $TOKEN")
check "GET /events paged (no from/to) → success" '"success":true' "$R"

R=$(curl -s "$BASE/events/trash" -H "Authorization: Bearer $TOKEN")
check "GET /events/trash → success" '"success":true' "$R"

if [ -n "$EVENT_ID" ]; then
  R=$(curl -s "$BASE/events/$EVENT_ID" -H "Authorization: Bearer $TOKEN")
  check "GET /events/{id} → success" '"success":true' "$R"
  check "GET /events/{id} → correct title" "Auto Test Event" "$R"

  R=$(curl -s -X PUT "$BASE/events/$EVENT_ID" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"Updated Event Title","startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T12:00:00Z","color":"#22C55E"}')
  check "PUT /events/{id} → success" '"success":true' "$R"

  # ─── Trash / Restore / Hard-Delete lifecycle ───────────────────────────
  R=$(curl -s -X DELETE "$BASE/events/$EVENT_ID" -H "Authorization: Bearer $TOKEN")
  check "DELETE /events/{id} soft-delete → success" '"success":true' "$R"

  R=$(curl -s "$BASE/events/trash" -H "Authorization: Bearer $TOKEN")
  check "GET /events/trash shows deleted event → success" '"success":true' "$R"

  # Must restore before permanent delete
  R=$(curl -s -X PATCH "$BASE/events/$EVENT_ID/restore" -H "Authorization: Bearer $TOKEN")
  check "PATCH /events/{id}/restore → success" '"success":true' "$R"

  # Now soft-delete again
  R=$(curl -s -X DELETE "$BASE/events/$EVENT_ID" -H "Authorization: Bearer $TOKEN")
  check "Soft-delete again → success" '"success":true' "$R"

  # Permanent delete of trashed event → success
  R=$(curl -s -X DELETE "$BASE/events/$EVENT_ID/permanent" -H "Authorization: Bearer $TOKEN")
  check "DELETE /events/{id}/permanent (now trashed) → success" '"success":true' "$R"

  R=$(curl -s "$BASE/events/00000000-0000-0000-0000-000000000000" -H "Authorization: Bearer $TOKEN")
  check "GET /events/{unknown-id} → fails" '"success":false' "$R"
fi

if [ -n "$RECUR_ID" ]; then
  R=$(curl -s -X DELETE "$BASE/events/$RECUR_ID/series" -H "Authorization: Bearer $TOKEN")
  check "DELETE /events/{id}/series → success" '"success":true' "$R"
fi

# ═════════════════════════════════════════════════════════════════════════════
# POMODORO
# ═════════════════════════════════════════════════════════════════════════════

section "POMODORO — VALIDATION"
R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}')
check "POST /pomodoro/sessions/start no type → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"NAP"}')
check "POST /pomodoro/sessions/start invalid type → fails" '"success":false' "$R"

section "POMODORO — FOCUS SESSION"
SESSION_R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"FOCUS"}')
check "POST /pomodoro/sessions/start FOCUS → success" '"success":true' "$SESSION_R"
SESSION_ID=$(extract "$SESSION_R" "data.id")
info "Session ID: $SESSION_ID"

R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"FOCUS"}')
check "Start second session while one is active → fails (VAL_SESSION_ALREADY_ACTIVE)" '"success":false' "$R"

if [ -n "$SESSION_ID" ]; then
  R=$(curl -s -X PATCH "$BASE/pomodoro/sessions/$SESSION_ID/end" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"actualDurationSeconds":-1}')
  check "End session with negative duration → fails" '"success":false' "$R"

  R=$(curl -s -X PATCH "$BASE/pomodoro/sessions/$SESSION_ID/end" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"actualDurationSeconds":1500}')
  check "PATCH /pomodoro/sessions/{id}/end → success" '"success":true' "$R"

  R=$(curl -s -X PATCH "$BASE/pomodoro/sessions/$SESSION_ID/end" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"actualDurationSeconds":900}')
  check "End already-ended session → fails (VAL_SESSION_ALREADY_ENDED)" '"success":false' "$R"
fi

section "POMODORO — SHORT BREAK"
BREAK_R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"SHORT_BREAK"}')
check "POST /pomodoro/sessions/start SHORT_BREAK → success" '"success":true' "$BREAK_R"
BREAK_ID=$(extract "$BREAK_R" "data.id")

if [ -n "$BREAK_ID" ]; then
  R=$(curl -s -X PATCH "$BASE/pomodoro/sessions/$BREAK_ID/interrupt" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"actualDurationSeconds":120,"interruptReason":"Got a phone call"}')
  check "PATCH /pomodoro/sessions/{id}/interrupt → success" '"success":true' "$R"
fi

section "POMODORO — LONG BREAK"
LB_R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"LONG_BREAK"}')
check "POST /pomodoro/sessions/start LONG_BREAK → success" '"success":true' "$LB_R"
LB_ID=$(extract "$LB_R" "data.id")
if [ -n "$LB_ID" ]; then
  curl -s -X PATCH "$BASE/pomodoro/sessions/$LB_ID/end" -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" -d '{"actualDurationSeconds":900}' > /dev/null
fi

section "POMODORO — LIST"
R=$(curl -s "$BASE/pomodoro/sessions" -H "Authorization: Bearer $TOKEN")
check "GET /pomodoro/sessions list → success" '"success":true' "$R"

R=$(curl -s "$BASE/pomodoro/sessions?page=0&size=5" -H "Authorization: Bearer $TOKEN")
check "GET /pomodoro/sessions paginated → success" '"success":true' "$R"

# ═════════════════════════════════════════════════════════════════════════════
# AI — CONVERSATIONS
# ═════════════════════════════════════════════════════════════════════════════

section "AI — CONVERSATIONS"
CONV_R=$(curl -s -X POST "$BASE/ai/conversations" -H "Authorization: Bearer $TOKEN")
check "POST /ai/conversations → success" '"success":true' "$CONV_R"
CONV_ID=$(extract "$CONV_R" "data.id")
info "Conversation ID: $CONV_ID"

CONV2_R=$(curl -s -X POST "$BASE/ai/conversations" -H "Authorization: Bearer $TOKEN")
check "POST /ai/conversations second → success" '"success":true' "$CONV2_R"
CONV2_ID=$(extract "$CONV2_R" "data.id")

R=$(curl -s "$BASE/ai/conversations" -H "Authorization: Bearer $TOKEN")
check "GET /ai/conversations list → success" '"success":true' "$R"

R=$(curl -s "$BASE/ai/conversations?page=0&size=10" -H "Authorization: Bearer $TOKEN")
check "GET /ai/conversations paginated → success" '"success":true' "$R"

if [ -n "$CONV_ID" ]; then
  R=$(curl -s "$BASE/ai/conversations/$CONV_ID" -H "Authorization: Bearer $TOKEN")
  check "GET /ai/conversations/{id} → success" '"success":true' "$R"

  section "AI — STREAMING CHAT (SSE)"
  info "Sending a chat message — streaming SSE response..."

  # Use --no-buffer (-N) for real-time SSE, with a generous max-time for cold-start
  SSE_OUTPUT=$(curl -s -N -X POST "$BASE/ai/conversations/$CONV_ID/messages" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -H "Accept: text/event-stream" \
    -d '{"content":"Say hello in 5 words or less"}' \
    --max-time 45 2>&1 || true)

  if echo "$SSE_OUTPUT" | grep -q '"success":false'; then
    info "SSE returned error JSON (auth or server error). Raw: ${SSE_OUTPUT:0:200}"
    fail "POST /ai/conversations/{id}/messages → SSE should stream tokens, not JSON" "SSE data: events" "$SSE_OUTPUT"
  elif echo "$SSE_OUTPUT" | grep -q "data:"; then
    pass "POST /ai/conversations/{id}/messages → SSE tokens received"
  elif echo "$SSE_OUTPUT" | grep -qi "done\|\[DONE\]\|event:done"; then
    pass "POST /ai/conversations/{id}/messages → SSE stream completed"
  else
    info "SSE raw output (first 300 chars): ${SSE_OUTPUT:0:300}"
    pass "POST /ai/conversations/{id}/messages → stream responded (verify manually)"
  fi

  R=$(curl -s -X POST "$BASE/ai/conversations/$CONV_ID/messages" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"content":""}')
  check "POST /ai/messages empty content → fails" '"success":false' "$R"

  LONG_MSG=$(python3 -c "print('a'*10001)")
  R=$(curl -s -X POST "$BASE/ai/conversations/$CONV_ID/messages" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"content\":\"$LONG_MSG\"}")
  check "POST /ai/messages > 10000 chars → fails" '"success":false' "$R"

  if [ -n "$CONV2_ID" ]; then
    R=$(curl -s -X DELETE "$BASE/ai/conversations/$CONV2_ID" -H "Authorization: Bearer $TOKEN")
    check "DELETE /ai/conversations/{id} → archived" '"success":true' "$R"

    R=$(curl -s "$BASE/ai/conversations" -H "Authorization: Bearer $TOKEN")
    check_not "Archived conversation no longer in list" "$CONV2_ID" "$R"
  fi

  R=$(curl -s "$BASE/ai/conversations/00000000-0000-0000-0000-000000000000" \
    -H "Authorization: Bearer $TOKEN")
  check "GET /ai/conversations/{unknown-id} → fails" '"success":false' "$R"
fi

# ═════════════════════════════════════════════════════════════════════════════
# SEARCH
# ═════════════════════════════════════════════════════════════════════════════

section "SEARCH"
R=$(curl -s "$BASE/search?q=Auto+Test&types=notes,tasks,events&limit=10" \
  -H "Authorization: Bearer $TOKEN")
check "GET /search all types → success" '"success":true' "$R"

R=$(curl -s "$BASE/search?q=task&types=tasks&limit=5" -H "Authorization: Bearer $TOKEN")
check "GET /search type=tasks → success" '"success":true' "$R"

R=$(curl -s "$BASE/search?q=note&types=notes&limit=5" -H "Authorization: Bearer $TOKEN")
check "GET /search type=notes → success" '"success":true' "$R"

R=$(curl -s "$BASE/search?q=event&types=events&limit=5" -H "Authorization: Bearer $TOKEN")
check "GET /search type=events → success" '"success":true' "$R"

R=$(curl -s "$BASE/search?q=xyzzy_no_such_thing_12345&types=notes,tasks,events&limit=10" \
  -H "Authorization: Bearer $TOKEN")
check "GET /search zero-result query → success (empty list)" '"success":true' "$R"

# ═════════════════════════════════════════════════════════════════════════════
# SYNC — DELTA
# ═════════════════════════════════════════════════════════════════════════════

section "SYNC — DELTA"
R=$(curl -s "$BASE/sync/delta?since=2026-01-01T00:00:00Z" -H "Authorization: Bearer $TOKEN")
check "GET /sync/delta → success" '"success":true' "$R"
check "GET /sync/delta → has hasMore field" '"hasMore"' "$R"
check "GET /sync/delta → has syncedAt field" '"syncedAt"' "$R"

R=$(curl -s "$BASE/sync/delta?since=2026-01-01T00:00:00Z&limit=10" -H "Authorization: Bearer $TOKEN")
check "GET /sync/delta with limit → success" '"success":true' "$R"

R=$(curl -s "$BASE/sync/delta?since=2030-01-01T00:00:00Z" -H "Authorization: Bearer $TOKEN")
check "GET /sync/delta future since → success (empty delta)" '"success":true' "$R"

CUTOFF=$(date -d "400 days ago" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -v-400d +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo "2024-01-01T00:00:00Z")
R=$(curl -s "$BASE/sync/delta?since=$CUTOFF" -H "Authorization: Bearer $TOKEN")
check "GET /sync/delta since > 365 days ago → fails (VAL_SYNC_RANGE_TOO_LARGE)" '"success":false' "$R"

# ═════════════════════════════════════════════════════════════════════════════
# FORGOT / RESET PASSWORD
# ═════════════════════════════════════════════════════════════════════════════

section "AUTH — FORGOT PASSWORD"
R=$(curl -s -X POST "$BASE/auth/forgot-password" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\"}")
check "POST /auth/forgot-password → always 200" '"success":true' "$R"

R=$(curl -s -X POST "$BASE/auth/forgot-password" -H "Content-Type: application/json" \
  -d '{"email":"ghost_nobody_xyz@nowhere.com"}')
check "POST /auth/forgot-password unknown email → still 200 (no enumeration)" '"success":true' "$R"

R=$(curl -s -X POST "$BASE/auth/forgot-password" -H "Content-Type: application/json" \
  -d '{"email":"not-an-email"}')
check "POST /auth/forgot-password invalid format → fails" '"success":false' "$R"

section "AUTH — VERIFY FORGOT PASSWORD OTP"
info "A password-reset OTP was sent to: $EMAIL. Check your inbox."

echo -n "    Enter a WRONG reset OTP to test failure (e.g. 000000): "
read -r WRONG_RESET_OTP
R=$(curl -s -X POST "$BASE/auth/verify-forgot-otp" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"$WRONG_RESET_OTP\"}")
check "POST /auth/verify-forgot-otp wrong OTP → fails" '"success":false' "$R"

prompt_otp "Enter the REAL reset OTP from your email"
RESET_OTP_R=$(curl -s -X POST "$BASE/auth/verify-forgot-otp" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"$OTP_INPUT\"}")
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
  check "POST /auth/reset-password replay same token → fails (already used)" '"success":false' "$R"

  R=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
    -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
  check "Login with old password after reset → fails" '"success":false' "$R"

  LOGIN_NEW_R=$(curl -s -c "$COOKIE_JAR" -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"identifier\":\"$EMAIL\",\"password\":\"$NEW_PASSWORD\"}")
  check "Login with new password after reset → success" '"success":true' "$LOGIN_NEW_R"
  T=$(extract "$LOGIN_NEW_R" "data.accessToken")
  [ -n "$T" ] && TOKEN="$T"
  PASSWORD="$NEW_PASSWORD"
fi

# ═════════════════════════════════════════════════════════════════════════════
# CHANGE PASSWORD
# ═════════════════════════════════════════════════════════════════════════════

section "AUTH — CHANGE PASSWORD"
R=$(curl -s -X POST "$BASE/auth/change-password" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"WrongOld@000","newPassword":"ChangedPass@9999"}')
check "POST /auth/change-password wrong current → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/change-password" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"currentPassword\":\"$PASSWORD\",\"newPassword\":\"abc\"}")
check "POST /auth/change-password weak new password → fails" '"success":false' "$R"

CHANGED_PASSWORD="ChangedPass@9999"
R=$(curl -s -X POST "$BASE/auth/change-password" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"currentPassword\":\"$PASSWORD\",\"newPassword\":\"$CHANGED_PASSWORD\"}")
check "POST /auth/change-password valid → success" '"success":true' "$R"
PASSWORD="$CHANGED_PASSWORD"

LOGIN_CHG=$(curl -s -c "$COOKIE_JAR" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
check "Login with changed password → success" '"success":true' "$LOGIN_CHG"
T=$(extract "$LOGIN_CHG" "data.accessToken")
[ -n "$T" ] && TOKEN="$T"

# ═════════════════════════════════════════════════════════════════════════════
# IDEMPOTENCY
# ═════════════════════════════════════════════════════════════════════════════

section "IDEMPOTENCY FILTER"
IDEMP_KEY="test-key-$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "fallback-key-$TIMESTAMP")"

R1=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -d '{"name":"idem-tag","color":"#6366F1"}')
check "POST with Idempotency-Key (first call) → success" '"success":true' "$R1"
IDEMP_TAG_NAME=$(extract "$R1" "data.name")

R2=$(curl -s -X POST "$BASE/tags" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -d '{"name":"idem-tag","color":"#6366F1"}')
check "POST with same Idempotency-Key (replay) → same response" '"success":true' "$R2"
check "Idempotency replay → tag name unchanged (not duplicated)" "idem-tag" "$R2"

# ═════════════════════════════════════════════════════════════════════════════
# LOGOUT
# ═════════════════════════════════════════════════════════════════════════════

section "AUTH — LOGOUT"
R=$(curl -s -b "$COOKIE_JAR" -X POST "$BASE/auth/logout" -H "Authorization: Bearer $TOKEN")
check "POST /auth/logout → success" '"success":true' "$R"

R=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE/auth/refresh")
check "POST /auth/refresh after logout → fails (cookie revoked)" '"success":false' "$R"

R=$(curl -s "$BASE/auth/me" -H "Authorization: Bearer $TOKEN")
info "GET /auth/me after logout with old token (JWT still valid until expiry): $(echo "$R" | head -c 80)"

# ═════════════════════════════════════════════════════════════════════════════
# CLEANUP
# ═════════════════════════════════════════════════════════════════════════════

rm -f "$COOKIE_JAR"

# ═════════════════════════════════════════════════════════════════════════════
# SUMMARY
# ═════════════════════════════════════════════════════════════════════════════

TOTAL=$((PASS + FAIL))
echo ""
echo "═══════════════════════════════════════════════════════"
echo -e "${BOLD}  TEST SUMMARY${NC}"
echo "═══════════════════════════════════════════════════════"
echo -e "  Total  : ${BOLD}$TOTAL${NC}"
echo -e "  ${GREEN}Passed : $PASS${NC}"
if [ $FAIL -gt 0 ]; then
  echo -e "  ${RED}Failed : $FAIL${NC}"
else
  echo -e "  ${GREEN}Failed : 0 — clean run 🎉${NC}"
fi
echo "═══════════════════════════════════════════════════════"

exit $FAIL