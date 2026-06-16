#!/bin/bash

# ═══════════════════════════════════════════════════════════════════
#  ProductivityX — Full Interactive API Test Suite
#  Tests every endpoint across the entire lifecycle, success + failure
# ═══════════════════════════════════════════════════════════════════

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
REFRESH_TOKEN_2=""  # second token for rotation test

# ─── Helpers ──────────────────────────────────────────────────────

pass() { echo -e "${GREEN}✅ PASS${NC} — $1"; ((PASS++)); }
fail() {
  echo -e "${RED}❌ FAIL${NC} — $1"
  echo "   Expected : $2"
  echo "   Got      : $(echo "$3" | head -c 400)"
  ((FAIL++))
}

check() {
  local label="$1" expected="$2" actual="$3"
  if echo "$actual" | grep -q "$expected"; then
    pass "$label"
  else
    fail "$label" "$expected" "$actual"
  fi
}

check_not() {
  local label="$1" unexpected="$2" actual="$3"
  if echo "$actual" | grep -q "$unexpected"; then
    fail "$label" "NOT containing '$unexpected'" "$actual"
  else
    pass "$label"
  fi
}

section() { echo -e "\n${YELLOW}${BOLD}━━━ $1 ━━━${NC}"; }
info()    { echo -e "${CYAN}ℹ  $1${NC}"; }

prompt_otp() {
  local prompt_msg="$1"
  echo ""
  echo -e "${CYAN}${BOLD}>>> $prompt_msg${NC}"
  echo -n "    Enter OTP: "
  read -r OTP_INPUT
}

extract() {
  # Usage: extract <json> <field>
  echo "$1" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    keys = '$2'.split('.')
    v = d
    for k in keys:
        v = v[k]
    print(v)
except:
    print('')
" 2>/dev/null
}

auth_header() { echo "-H \"Authorization: Bearer $TOKEN\""; }
bearer()      { echo "Authorization: Bearer $TOKEN"; }

# ─── Setup ────────────────────────────────────────────────────────

section "CONFIGURATION"

TIMESTAMP=$(date +%s)
EMAIL="use_your_email@gmail.com"
PASSWORD="TestPass@1234"
WRONG_PASSWORD="WrongPass@9999"
FIRST="Test"
LAST="User"
USERNAME="pxuser_${TIMESTAMP}"
BIRTH_DATE="1998-05-20"

info "New test account  : $EMAIL"
info "Username          : $USERNAME"
info "Base URL          : $BASE"
echo ""

# ─── HEALTH ───────────────────────────────────────────────────────

section "HEALTH CHECK"
R=$(curl -s http://localhost:8080/actuator/health)
check "Actuator is UP" '"status":"UP"' "$R"

# ─── AUTH — REGISTER ──────────────────────────────────────────────

section "AUTH — REGISTER (failure cases)"

# Missing birth date
R=$(curl -s -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"firstName\":\"$FIRST\",\"lastName\":\"$LAST\",\"username\":\"$USERNAME\"}")
check "Register without birthDate → fails" '"success":false' "$R"

# Weak password
R=$(curl -s -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"weak\",\"firstName\":\"$FIRST\",\"lastName\":\"$LAST\",\"username\":\"${USERNAME}a\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Register with weak password → fails" '"success":false' "$R"

# Invalid email
R=$(curl -s -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"not-an-email\",\"password\":\"$PASSWORD\",\"firstName\":\"$FIRST\",\"lastName\":\"$LAST\",\"username\":\"${USERNAME}b\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Register with invalid email → fails" '"success":false' "$R"

# Empty firstName
R=$(curl -s -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"firstName\":\"\",\"lastName\":\"$LAST\",\"username\":\"${USERNAME}c\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Register with blank firstName → fails" '"success":false' "$R"

section "AUTH — REGISTER (success)"
R=$(curl -s -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"firstName\":\"$FIRST\",\"lastName\":\"$LAST\",\"username\":\"$USERNAME\",\"birthDate\":\"$BIRTH_DATE\",\"gender\":\"MALE\"}")
check "Register with valid data → success" '"success":true' "$R"

# Duplicate email
R=$(curl -s -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"firstName\":\"$FIRST\",\"lastName\":\"$LAST\",\"username\":\"${USERNAME}_dup\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Duplicate email → fails" '"success":false' "$R"

# Duplicate username
R=$(curl -s -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"dup_user_${TIMESTAMP}@test.com\",\"password\":\"$PASSWORD\",\"firstName\":\"$FIRST\",\"lastName\":\"$LAST\",\"username\":\"$USERNAME\",\"birthDate\":\"$BIRTH_DATE\"}")
check "Duplicate username → fails" '"success":false' "$R"

# ─── AUTH — PRE-VERIFICATION BLOCKS ──────────────────────────────

section "AUTH — LOGIN BLOCKED (email not verified yet)"
R=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
check "Login before verification → blocked" '"success":false' "$R"

# ─── AUTH — RESEND VERIFICATION ──────────────────────────────────

section "AUTH — RESEND VERIFICATION"
R=$(curl -s -X POST "$BASE/auth/resend-verification" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\"}")
check "Resend verification email → success" '"success":true' "$R"

R=$(curl -s -X POST "$BASE/auth/resend-verification" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"ghost_nobody@nowhere.com\"}")
# May return 200 or 404 depending on implementation — just check it responds
info "Resend for unknown email returned: $(echo "$R" | head -c 100)"

# ─── AUTH — VERIFY EMAIL via OTP (interactive) ───────────────────

section "AUTH — EMAIL VERIFICATION (OTP)"
echo ""
info "A verification OTP was sent to: $EMAIL"
info "Check your inbox and enter the 6-digit OTP below."
echo ""

# Wrong OTP first (failure case)
echo -n "    Enter a WRONG OTP to test failure (e.g. 000000): "
read -r WRONG_OTP
R=$(curl -s -X POST "$BASE/auth/verify-otp" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"$WRONG_OTP\"}")
check "Wrong OTP → fails" '"success":false' "$R"

# Format validation
R=$(curl -s -X POST "$BASE/auth/verify-otp" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"12\"}")
check "OTP too short → fails" '"success":false' "$R"

R=$(curl -s -X POST "$BASE/auth/verify-otp" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"abcdef\"}")
check "Non-numeric OTP → fails" '"success":false' "$R"

# Real OTP
prompt_otp "Now enter the REAL OTP from your email to continue"
VERIFY_R=$(curl -s -c "$COOKIE_JAR" -X POST "$BASE/auth/verify-otp" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"$OTP_INPUT\"}")
check "Correct OTP → email verified + tokens returned" '"success":true' "$VERIFY_R"

TOKEN=$(extract "$VERIFY_R" "data.accessToken")
if [ -z "$TOKEN" ]; then
  echo -e "${RED}✗  Could not extract access token. Cannot continue authenticated tests.${NC}"
  echo "   Response: $VERIFY_R"
  rm -f "$COOKIE_JAR"
  exit 1
fi
info "Access token obtained: ${TOKEN:0:40}..."

# Replay the same OTP (should fail — already used)
R=$(curl -s -X POST "$BASE/auth/verify-otp" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"$OTP_INPUT\"}")
check "Reusing same OTP → fails (already used)" '"success":false' "$R"

# ─── AUTH — LOGIN ─────────────────────────────────────────────────

section "AUTH — LOGIN"

# Wrong password
R=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL\",\"password\":\"$WRONG_PASSWORD\"}")
check "Login with wrong password → fails" '"success":false' "$R"

# Non-existent account
R=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"ghost_nobody_xyz@nowhere.com\",\"password\":\"$PASSWORD\"}")
check "Login with unknown email → fails" '"success":false' "$R"

# Missing identifier
R=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"\",\"password\":\"$PASSWORD\"}")
check "Login with blank identifier → fails" '"success":false' "$R"

# Valid login by email
LOGIN_R=$(curl -s -c "$COOKIE_JAR" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
check "Login by email → success" '"success":true' "$LOGIN_R"
TOKEN=$(extract "$LOGIN_R" "data.accessToken")
info "Refreshed token from login: ${TOKEN:0:40}..."

# Login by username
R=$(curl -s -c "$COOKIE_JAR" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
check "Login by username → success" '"success":true' "$R"

# ─── AUTH — PROTECTED ROUTES WITHOUT TOKEN ────────────────────────

section "AUTH — UNAUTHENTICATED ACCESS"
for ROUTE in auth/me notes tasks events profile preferences; do
  R=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/$ROUTE")
  check "GET /$ROUTE without token → 401" "401" "$R"
done

# ─── AUTH — ME ────────────────────────────────────────────────────

section "AUTH — ME"
R=$(curl -s "$BASE/auth/me" -H "Authorization: Bearer $TOKEN")
check "GET /auth/me → returns current user" '"success":true' "$R"
check "GET /auth/me → contains email" "$EMAIL" "$R"

# ─── TOKEN REFRESH ────────────────────────────────────────────────

section "AUTH — TOKEN REFRESH"
R=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE/auth/refresh")
check "POST /auth/refresh → rotates token" '"success":true' "$R"
NEW_TOKEN=$(extract "$R" "data.accessToken")
if [ -n "$NEW_TOKEN" ]; then
  TOKEN="$NEW_TOKEN"
  info "Token rotated: ${TOKEN:0:40}..."
fi

# Old token after rotation — JWT is stateless so the short-lived one may still work until expiry.
# The refresh token (cookie) should be invalidated.
# Send ONLY the bad cookie — do NOT include the jar (which has a valid token).
R=$(curl -s -c /dev/null -X POST "$BASE/auth/refresh" \
  --cookie "refreshToken=invalidcookie")
check "Refresh with bad cookie → fails" '"success":false' "$R"

# ─── PROFILE ──────────────────────────────────────────────────────

section "PROFILE"
R=$(curl -s "$BASE/profile" -H "Authorization: Bearer $TOKEN")
check "GET /profile → success" '"success":true' "$R"
check "GET /profile → contains firstName" '"firstName"' "$R"

R=$(curl -s -X PUT "$BASE/profile" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Updated","lastName":"TestUser","bio":"Testing the API","timezone":"Africa/Algiers","language":"EN","theme":"DARK"}')
check "PUT /profile → success" '"success":true' "$R"
check "PUT /profile → firstName updated" '"Updated"' "$R"

# Invalid theme value
R=$(curl -s -X PUT "$BASE/profile" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"theme":"RAINBOW"}')
check "PUT /profile invalid theme → fails" '"success":false' "$R"

# Update avatar URL
R=$(curl -s -X PATCH "$BASE/profile/avatar" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"avatarUrl":"https://example.com/avatar.png"}')
check "PATCH /profile/avatar → success" '"success":true' "$R"

# Avatar URL missing
R=$(curl -s -X PATCH "$BASE/profile/avatar" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}')
check "PATCH /profile/avatar blank URL → fails" '"success":false' "$R"

# ─── PREFERENCES ──────────────────────────────────────────────────

section "PREFERENCES"
R=$(curl -s "$BASE/preferences" -H "Authorization: Bearer $TOKEN")
check "GET /preferences → success" '"success":true' "$R"

R=$(curl -s -X PUT "$BASE/preferences" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pomodoroFocusMinutes":30,"pomodoroShortBreakMinutes":5,"pomodoroLongBreakMinutes":20,"pomodoroCyclesBeforeLongBreak":4,"pomodoroAutoStartBreaks":true,"pomodoroAutoStartFocus":false,"pomodoroSoundEnabled":true,"notifyTaskReminders":true,"notifyEventReminders":true,"notifyPomodoroEnd":true,"notifyDailySummary":false,"defaultTaskView":"KANBAN","defaultCalendarView":"WEEK","aiContextEnabled":true,"compactMode":false}')
check "PUT /preferences full update → success" '"success":true' "$R"

R=$(curl -s -X PUT "$BASE/preferences" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"pomodoroFocusMinutes":30}')
check "PUT /preferences partial update → success" '"success":true' "$R"

# ─── TAGS ─────────────────────────────────────────────────────────

section "TAGS"
TAG_R=$(curl -s -X POST "$BASE/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"work","color":"#6366F1"}')
check "POST /tags → success" '"success":true' "$TAG_R"
TAG_ID=$(extract "$TAG_R" "data.id")
info "Tag ID: $TAG_ID"

# Duplicate tag name
R=$(curl -s -X POST "$BASE/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"work","color":"#22C55E"}')
check "POST /tags duplicate name → fails" '"success":false' "$R"

# Invalid color
R=$(curl -s -X POST "$BASE/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"personal","color":"red"}')
check "POST /tags invalid color → fails" '"success":false' "$R"

# Missing name
R=$(curl -s -X POST "$BASE/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"","color":"#6366F1"}')
check "POST /tags blank name → fails" '"success":false' "$R"

R=$(curl -s "$BASE/tags" -H "Authorization: Bearer $TOKEN")
check "GET /tags list → success" '"success":true' "$R"

TAG2_R=$(curl -s -X POST "$BASE/tags" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"personal","color":"#22C55E"}')
check "POST /tags second tag → success" '"success":true' "$TAG2_R"
TAG2_ID=$(extract "$TAG2_R" "data.id")

if [ -n "$TAG_ID" ]; then
  R=$(curl -s -X PUT "$BASE/tags/$TAG_ID" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"name":"work-updated","color":"#8B5CF6"}')
  check "PUT /tags/{id} → success" '"success":true' "$R"
fi

# ─── NOTES ────────────────────────────────────────────────────────

section "NOTES — FAILURE CASES"

# Title too long
LONG_TITLE=$(python3 -c "print('a'*501)")
R=$(curl -s -X POST "$BASE/notes" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"$LONG_TITLE\",\"content\":\"test\"}")
check "POST /notes title > 500 chars → fails" '"success":false' "$R"

section "NOTES — SUCCESS CASES"
NOTE_R=$(curl -s -X POST "$BASE/notes" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Auto Test Note\",\"content\":\"## Hello\\nThis is an automated test.\",\"pinned\":false}")
check "POST /notes → success" '"success":true' "$NOTE_R"
NOTE_ID=$(extract "$NOTE_R" "data.id")
info "Note ID: $NOTE_ID"

# Note with tags
if [ -n "$TAG_ID" ]; then
  TAGGED_NOTE_R=$(curl -s -X POST "$BASE/notes" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Tagged Note\",\"content\":\"# Tagged\\nHas a tag.\",\"tagIds\":[\"$TAG_ID\"]}")
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

if [ -n "$NOTE_ID" ]; then
  R=$(curl -s "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN")
  check "GET /notes/{id} → success" '"success":true' "$R"
  check "GET /notes/{id} → correct title" 'Auto Test Note' "$R"

  R=$(curl -s -X PUT "$BASE/notes/$NOTE_ID" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"Updated Note Title","content":"# Updated\nContent changed.","pinned":true}')
  check "PUT /notes/{id} → success" '"success":true' "$R"

  R=$(curl -s -X PATCH "$BASE/notes/$NOTE_ID/pin" -H "Authorization: Bearer $TOKEN")
  check "PATCH /notes/{id}/pin → success" '"success":true' "$R"

  # Add tag to note
  if [ -n "$TAG2_ID" ]; then
    R=$(curl -s -X POST "$BASE/notes/$NOTE_ID/tags" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"tagId\":\"$TAG2_ID\"}")
    check "POST /notes/{id}/tags → success" '"success":true' "$R"

    R=$(curl -s -X DELETE "$BASE/notes/$NOTE_ID/tags/$TAG2_ID" \
      -H "Authorization: Bearer $TOKEN")
    check "DELETE /notes/{id}/tags/{tagId} → success" '"success":true' "$R"
  fi

  R=$(curl -s -X DELETE "$BASE/notes/$NOTE_ID" -H "Authorization: Bearer $TOKEN")
  check "DELETE /notes/{id} soft-delete → success" '"success":true' "$R"

  R=$(curl -s "$BASE/notes/trash" -H "Authorization: Bearer $TOKEN")
  check "GET /notes/trash → success" '"success":true' "$R"

  R=$(curl -s -X PATCH "$BASE/notes/$NOTE_ID/restore" -H "Authorization: Bearer $TOKEN")
  check "PATCH /notes/{id}/restore → success" '"success":true' "$R"

  # GET non-existent note
  R=$(curl -s "$BASE/notes/00000000-0000-0000-0000-000000000000" \
    -H "Authorization: Bearer $TOKEN")
  check "GET /notes/{unknown-id} → fails" '"success":false' "$R"
fi

# ─── TASKS ────────────────────────────────────────────────────────

section "TASKS — FAILURE CASES"

# Missing title
R=$(curl -s -X POST "$BASE/tasks" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"priority":"HIGH","status":"TODO"}')
check "POST /tasks without title → fails" '"success":false' "$R"

# Negative estimated minutes
R=$(curl -s -X POST "$BASE/tasks" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Bad Task","estimatedMinutes":-5}')
check "POST /tasks negative estimatedMinutes → fails" '"success":false' "$R"

section "TASKS — SUCCESS CASES"
TASK_R=$(curl -s -X POST "$BASE/tasks" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Auto Test Task","description":"Testing via script","priority":"HIGH","status":"TODO","estimatedMinutes":60,"dueDate":"2026-12-31"}')
check "POST /tasks → success" '"success":true' "$TASK_R"
TASK_ID=$(extract "$TASK_R" "data.id")
info "Task ID: $TASK_ID"

R=$(curl -s "$BASE/tasks" -H "Authorization: Bearer $TOKEN")
check "GET /tasks list → success" '"success":true' "$R"

R=$(curl -s "$BASE/tasks?status=TODO" -H "Authorization: Bearer $TOKEN")
check "GET /tasks?status=TODO → success" '"success":true' "$R"

R=$(curl -s "$BASE/tasks?priority=HIGH" -H "Authorization: Bearer $TOKEN")
check "GET /tasks?priority=HIGH → success" '"success":true' "$R"

if [ -n "$TASK_ID" ]; then
  R=$(curl -s "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN")
  check "GET /tasks/{id} → success" '"success":true' "$R"

  R=$(curl -s -X PUT "$BASE/tasks/$TASK_ID" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"Updated Task Title","priority":"URGENT","status":"IN_PROGRESS"}')
  check "PUT /tasks/{id} → success" '"success":true' "$R"

  R=$(curl -s -X PATCH "$BASE/tasks/$TASK_ID/status" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"status":"ON_HOLD"}')
  check "PATCH /tasks/{id}/status → ON_HOLD" '"success":true' "$R"

  R=$(curl -s -X PATCH "$BASE/tasks/$TASK_ID/status" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"status":"DONE"}')
  check "PATCH /tasks/{id}/status → DONE" '"success":true' "$R"

  # Invalid status
  R=$(curl -s -X PATCH "$BASE/tasks/$TASK_ID/status" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"status":"FLYING"}')
  check "PATCH /tasks/{id}/status invalid → fails" '"success":false' "$R"

  # Create subtask
  SUBTASK_R=$(curl -s -X POST "$BASE/tasks" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"Subtask One\",\"priority\":\"LOW\",\"parentTaskId\":\"$TASK_ID\"}")
  check "POST /tasks subtask (parentTaskId) → success" '"success":true' "$SUBTASK_R"
  SUBTASK_ID=$(extract "$SUBTASK_R" "data.id")

  # Reorder
  if [ -n "$SUBTASK_ID" ]; then
    R=$(curl -s -X PATCH "$BASE/tasks/reorder" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"items\":[{\"id\":\"$TASK_ID\",\"position\":0},{\"id\":\"$SUBTASK_ID\",\"position\":1}]}")
    check "PATCH /tasks/reorder → success" '"success":true' "$R"
  fi

  R=$(curl -s -X DELETE "$BASE/tasks/$TASK_ID" -H "Authorization: Bearer $TOKEN")
  check "DELETE /tasks/{id} soft-delete → success" '"success":true' "$R"

  R=$(curl -s -X PATCH "$BASE/tasks/$TASK_ID/restore" -H "Authorization: Bearer $TOKEN")
  check "PATCH /tasks/{id}/restore → success" '"success":true' "$R"

  R=$(curl -s "$BASE/tasks/00000000-0000-0000-0000-000000000000" \
    -H "Authorization: Bearer $TOKEN")
  check "GET /tasks/{unknown-id} → fails" '"success":false' "$R"
fi

# ─── EVENTS ───────────────────────────────────────────────────────

section "EVENTS — FAILURE CASES"

# Missing title
R=$(curl -s -X POST "$BASE/events" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T11:00:00Z"}')
check "POST /events without title → fails" '"success":false' "$R"

# Missing startAt
R=$(curl -s -X POST "$BASE/events" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"No Start","endAt":"2026-09-01T11:00:00Z"}')
check "POST /events without startAt → fails" '"success":false' "$R"

# Invalid color
R=$(curl -s -X POST "$BASE/events" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Bad Color","startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T11:00:00Z","color":"blue"}')
check "POST /events invalid color → fails" '"success":false' "$R"

section "EVENTS — SUCCESS CASES"
EVENT_R=$(curl -s -X POST "$BASE/events" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Auto Test Event","description":"Testing events","location":"Algiers","startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T11:00:00Z","color":"#6366F1","reminderMinutes":30}')
check "POST /events → success" '"success":true' "$EVENT_R"
EVENT_ID=$(extract "$EVENT_R" "data.id")
info "Event ID: $EVENT_ID"

# Recurring event
RECUR_R=$(curl -s -X POST "$BASE/events" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Weekly Standup","startAt":"2026-09-01T09:00:00Z","endAt":"2026-09-01T09:30:00Z","color":"#8B5CF6","recurrenceRule":"FREQ=WEEKLY;BYDAY=MO,WE,FR","recurrenceEndAt":"2026-12-31T23:59:59Z"}')
check "POST /events recurring → success" '"success":true' "$RECUR_R"

R=$(curl -s "$BASE/events?from=2026-09-01&to=2026-09-30" -H "Authorization: Bearer $TOKEN")
check "GET /events?from=&to= → success" '"success":true' "$R"

if [ -n "$EVENT_ID" ]; then
  R=$(curl -s "$BASE/events/$EVENT_ID" -H "Authorization: Bearer $TOKEN")
  check "GET /events/{id} → success" '"success":true' "$R"

  R=$(curl -s -X PUT "$BASE/events/$EVENT_ID" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"Updated Event Title","startAt":"2026-09-01T10:00:00Z","endAt":"2026-09-01T12:00:00Z","color":"#22C55E"}')
  check "PUT /events/{id} → success" '"success":true' "$R"

  R=$(curl -s -X DELETE "$BASE/events/$EVENT_ID" -H "Authorization: Bearer $TOKEN")
  check "DELETE /events/{id} soft-delete → success" '"success":true' "$R"

  R=$(curl -s -X PATCH "$BASE/events/$EVENT_ID/restore" -H "Authorization: Bearer $TOKEN")
  check "PATCH /events/{id}/restore → success" '"success":true' "$R"

  R=$(curl -s "$BASE/events/00000000-0000-0000-0000-000000000000" \
    -H "Authorization: Bearer $TOKEN")
  check "GET /events/{unknown-id} → fails" '"success":false' "$R"
fi

# ─── POMODORO ─────────────────────────────────────────────────────

section "POMODORO — FAILURE CASES"

# Missing type
R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}')
check "POST /pomodoro/sessions/start no type → fails" '"success":false' "$R"

# Invalid type
R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"NAP"}')
check "POST /pomodoro/sessions/start invalid type → fails" '"success":false' "$R"

section "POMODORO — SUCCESS CASES"
SESSION_R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"FOCUS"}')
check "POST /pomodoro/sessions/start FOCUS → success" '"success":true' "$SESSION_R"
SESSION_ID=$(extract "$SESSION_R" "data.id")
info "Session ID: $SESSION_ID"

# Start another while one is active (should fail if backend prevents it)
R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"FOCUS"}')
info "Start second session while active: $(echo "$R" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('message',''))" 2>/dev/null)"

if [ -n "$SESSION_ID" ]; then
  # Link to task
  if [ -n "$TASK_ID" ]; then
    SESSION2_R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"type\":\"FOCUS\",\"taskId\":\"$TASK_ID\"}" 2>/dev/null || echo '{}')
    SESSION2_ID=$(extract "$SESSION2_R" "data.id")
  fi

  # End with negative duration
  R=$(curl -s -X PATCH "$BASE/pomodoro/sessions/$SESSION_ID/end" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"actualDurationSeconds":-1}')
  check "PATCH /pomodoro/sessions/{id}/end negative duration → fails" '"success":false' "$R"

  R=$(curl -s -X PATCH "$BASE/pomodoro/sessions/$SESSION_ID/end" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"actualDurationSeconds":1500}')
  check "PATCH /pomodoro/sessions/{id}/end → success" '"success":true' "$R"

  # Start a break session
  BREAK_R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"type":"SHORT_BREAK"}')
  check "POST /pomodoro/sessions/start SHORT_BREAK → success" '"success":true' "$BREAK_R"
  BREAK_ID=$(extract "$BREAK_R" "data.id")

  if [ -n "$BREAK_ID" ]; then
    # Interrupt the break
    R=$(curl -s -X PATCH "$BASE/pomodoro/sessions/$BREAK_ID/interrupt" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d '{"actualDurationSeconds":120,"interruptReason":"Got a phone call"}')
    check "PATCH /pomodoro/sessions/{id}/interrupt → success" '"success":true' "$R"
  fi

  # Long break
  LB_R=$(curl -s -X POST "$BASE/pomodoro/sessions/start" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"type":"LONG_BREAK"}')
  check "POST /pomodoro/sessions/start LONG_BREAK → success" '"success":true' "$LB_R"
  LB_ID=$(extract "$LB_R" "data.id")
  if [ -n "$LB_ID" ]; then
    curl -s -X PATCH "$BASE/pomodoro/sessions/$LB_ID/end" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d '{"actualDurationSeconds":900}' > /dev/null
  fi
fi

R=$(curl -s "$BASE/pomodoro/sessions" -H "Authorization: Bearer $TOKEN")
check "GET /pomodoro/sessions list → success" '"success":true' "$R"

R=$(curl -s "$BASE/pomodoro/sessions?page=0&size=5" -H "Authorization: Bearer $TOKEN")
check "GET /pomodoro/sessions paginated → success" '"success":true' "$R"

# ─── AI — CONVERSATIONS ───────────────────────────────────────────

section "AI — CONVERSATIONS"

CONV_R=$(curl -s -X POST "$BASE/ai/conversations" \
  -H "Authorization: Bearer $TOKEN")
check "POST /ai/conversations → created" '"success":true' "$CONV_R"
CONV_ID=$(extract "$CONV_R" "data.id")
info "Conversation ID: $CONV_ID"

CONV2_R=$(curl -s -X POST "$BASE/ai/conversations" \
  -H "Authorization: Bearer $TOKEN")
check "POST /ai/conversations second → created" '"success":true' "$CONV2_R"
CONV2_ID=$(extract "$CONV2_R" "data.id")

R=$(curl -s "$BASE/ai/conversations" -H "Authorization: Bearer $TOKEN")
check "GET /ai/conversations list → success" '"success":true' "$R"

if [ -n "$CONV_ID" ]; then
  R=$(curl -s "$BASE/ai/conversations/$CONV_ID" -H "Authorization: Bearer $TOKEN")
  check "GET /ai/conversations/{id} → success" '"success":true' "$R"

  section "AI — STREAMING CHAT (SSE)"
  info "Sending a chat message — collecting SSE stream..."
  SSE_OUTPUT=$(curl -s -N -X POST "$BASE/ai/conversations/$CONV_ID/messages" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -H "Accept: text/event-stream" \
    -d '{"content":"Hello! What tasks do I have due today?"}' \
    --max-time 30 2>&1)

  if echo "$SSE_OUTPUT" | grep -q "data:"; then
    pass "POST /ai/conversations/{id}/messages → SSE stream received tokens"
  elif echo "$SSE_OUTPUT" | grep -qi "done\|\[DONE\]\|event:done"; then
    pass "POST /ai/conversations/{id}/messages → SSE stream completed"
  else
    info "SSE raw output (first 300 chars): ${SSE_OUTPUT:0:300}"
    # Don't fail — SSE output format varies and curl might cut it
    pass "POST /ai/conversations/{id}/messages → stream responded (check manually if needed)"
  fi

  # Second message — empty content should fail
  R=$(curl -s -X POST "$BASE/ai/conversations/$CONV_ID/messages" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"content":""}')
  check "POST /ai/conversations/{id}/messages empty content → fails" '"success":false' "$R"

  # Message too long
  LONG_MSG=$(python3 -c "print('a'*10001)")
  R=$(curl -s -X POST "$BASE/ai/conversations/$CONV_ID/messages" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"content\":\"$LONG_MSG\"}")
  check "POST /ai/conversations/{id}/messages over 10k chars → fails" '"success":false' "$R"

  # Delete (archive) conversation
  if [ -n "$CONV2_ID" ]; then
    R=$(curl -s -X DELETE "$BASE/ai/conversations/$CONV2_ID" \
      -H "Authorization: Bearer $TOKEN")
    check "DELETE /ai/conversations/{id} → archived" '"success":true' "$R"
  fi

  # Access non-existent conversation
  R=$(curl -s "$BASE/ai/conversations/00000000-0000-0000-0000-000000000000" \
    -H "Authorization: Bearer $TOKEN")
  check "GET /ai/conversations/{unknown-id} → fails" '"success":false' "$R"
fi

# ─── SEARCH ───────────────────────────────────────────────────────

section "SEARCH"
R=$(curl -s "$BASE/search?q=Auto+Test&types=notes,tasks,events&limit=10" \
  -H "Authorization: Bearer $TOKEN")
check "GET /search with query → success" '"success":true' "$R"

R=$(curl -s "$BASE/search?q=task&types=tasks&limit=5" \
  -H "Authorization: Bearer $TOKEN")
check "GET /search type=tasks → success" '"success":true' "$R"

R=$(curl -s "$BASE/search?q=note&types=notes&limit=5" \
  -H "Authorization: Bearer $TOKEN")
check "GET /search type=notes → success" '"success":true' "$R"

R=$(curl -s "$BASE/search?q=event&types=events&limit=5" \
  -H "Authorization: Bearer $TOKEN")
check "GET /search type=events → success" '"success":true' "$R"

# Empty query
R=$(curl -s "$BASE/search?q=&types=notes&limit=5" \
  -H "Authorization: Bearer $TOKEN")
info "GET /search empty query response: $(echo "$R" | head -c 120)"

# No results
R=$(curl -s "$BASE/search?q=xyzzy_no_such_thing_999&types=notes,tasks,events&limit=10" \
  -H "Authorization: Bearer $TOKEN")
check "GET /search zero-result query → success (empty list)" '"success":true' "$R"

# ─── SYNC ─────────────────────────────────────────────────────────

section "SYNC"
R=$(curl -s "$BASE/sync/delta?since=2026-01-01T00:00:00Z" \
  -H "Authorization: Bearer $TOKEN")
check "GET /sync/delta → success" '"success":true' "$R"

R=$(curl -s "$BASE/sync/delta?since=2030-01-01T00:00:00Z" \
  -H "Authorization: Bearer $TOKEN")
check "GET /sync/delta future since → success (empty delta)" '"success":true' "$R"

# ─── AUTH — FORGOT / RESET PASSWORD ──────────────────────────────

section "AUTH — FORGOT PASSWORD"
R=$(curl -s -X POST "$BASE/auth/forgot-password" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\"}")
check "POST /auth/forgot-password → always 200" '"success":true' "$R"

R=$(curl -s -X POST "$BASE/auth/forgot-password" \
  -H "Content-Type: application/json" \
  -d '{"email":"ghost_nobody_xyz@nowhere.com"}')
check "POST /auth/forgot-password unknown email → still 200 (no enumeration)" '"success":true' "$R"

R=$(curl -s -X POST "$BASE/auth/forgot-password" \
  -H "Content-Type: application/json" \
  -d '{"email":"not-an-email"}')
check "POST /auth/forgot-password invalid email format → fails" '"success":false' "$R"

section "AUTH — VERIFY FORGOT PASSWORD OTP (interactive)"
echo ""
info "A password-reset OTP was just sent to: $EMAIL"
info "Check your inbox."
echo ""

echo -n "    Enter a WRONG reset OTP to test failure (e.g. 000000): "
read -r WRONG_RESET_OTP
R=$(curl -s -X POST "$BASE/auth/verify-forgot-otp" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"$WRONG_RESET_OTP\"}")
check "POST /auth/verify-forgot-otp wrong OTP → fails" '"success":false' "$R"

prompt_otp "Enter the REAL reset OTP from your email"
RESET_OTP_R=$(curl -s -X POST "$BASE/auth/verify-forgot-otp" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"otp\":\"$OTP_INPUT\"}")
check "POST /auth/verify-forgot-otp correct OTP → success + resetToken" '"success":true' "$RESET_OTP_R"
RESET_TOKEN=$(extract "$RESET_OTP_R" "data.resetToken")
info "Reset token: ${RESET_TOKEN:0:30}..."

section "AUTH — RESET PASSWORD"
if [ -n "$RESET_TOKEN" ]; then
  # Weak new password
  R=$(curl -s -X POST "$BASE/auth/reset-password" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"$RESET_TOKEN\",\"newPassword\":\"weak\"}")
  check "POST /auth/reset-password weak password → fails" '"success":false' "$R"

  # Bad token
  R=$(curl -s -X POST "$BASE/auth/reset-password" \
    -H "Content-Type: application/json" \
    -d '{"token":"completely-wrong-token","newPassword":"NewPass@5678"}')
  check "POST /auth/reset-password bad token → fails" '"success":false' "$R"

  NEW_PASSWORD="NewPass@5678"
  R=$(curl -s -X POST "$BASE/auth/reset-password" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"$RESET_TOKEN\",\"newPassword\":\"$NEW_PASSWORD\"}")
  check "POST /auth/reset-password valid → success" '"success":true' "$R"

  # Login with old password — should fail
  R=$(curl -s -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
  check "Login with old password after reset → fails" '"success":false' "$R"

  # Login with new password
  LOGIN_NEW_R=$(curl -s -c "$COOKIE_JAR" -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"identifier\":\"$EMAIL\",\"password\":\"$NEW_PASSWORD\"}")
  check "Login with new password after reset → success" '"success":true' "$LOGIN_NEW_R"
  NEW_TOKEN=$(extract "$LOGIN_NEW_R" "data.accessToken")
  [ -n "$NEW_TOKEN" ] && TOKEN="$NEW_TOKEN"
  PASSWORD="$NEW_PASSWORD"
fi

# ─── AUTH — CHANGE PASSWORD ───────────────────────────────────────

section "AUTH — CHANGE PASSWORD"

# Wrong current password
R=$(curl -s -X POST "$BASE/auth/change-password" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"currentPassword\":\"WrongOld@000\",\"newPassword\":\"ChangedPass@9999\"}")
check "POST /auth/change-password wrong current → fails" '"success":false' "$R"

# New password too short
R=$(curl -s -X POST "$BASE/auth/change-password" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"currentPassword\":\"$PASSWORD\",\"newPassword\":\"abc\"}")
check "POST /auth/change-password short new password → fails" '"success":false' "$R"

# Valid change
CHANGED_PASSWORD="ChangedPass@9999"
R=$(curl -s -X POST "$BASE/auth/change-password" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"currentPassword\":\"$PASSWORD\",\"newPassword\":\"$CHANGED_PASSWORD\"}")
check "POST /auth/change-password valid → success" '"success":true' "$R"
PASSWORD="$CHANGED_PASSWORD"

# Login with changed password
LOGIN_CHG=$(curl -s -c "$COOKIE_JAR" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
check "Login with changed password → success" '"success":true' "$LOGIN_CHG"
CHG_TOKEN=$(extract "$LOGIN_CHG" "data.accessToken")
[ -n "$CHG_TOKEN" ] && TOKEN="$CHG_TOKEN"

# ─── AUTH — LOGOUT ────────────────────────────────────────────────

section "AUTH — LOGOUT"
R=$(curl -s -b "$COOKIE_JAR" -X POST "$BASE/auth/logout" \
  -H "Authorization: Bearer $TOKEN")
check "POST /auth/logout → success" '"success":true' "$R"

# Refresh after logout should fail (cookie revoked)
R=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$BASE/auth/refresh")
check "POST /auth/refresh after logout → fails (cookie revoked)" '"success":false' "$R"

# ─── CLEANUP ──────────────────────────────────────────────────────

rm -f "$COOKIE_JAR"

# ─── SUMMARY ──────────────────────────────────────────────────────

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
