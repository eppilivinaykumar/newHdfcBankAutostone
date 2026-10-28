#!/usr/bin/env bash
# AutoStone enterprise remote execution wrapper v4.
# start  <run_id> <job_id> <execution_id> <callback_url> <timeout_seconds> <working_directory> <command> [callback_secret_file]
# signal <pid> <TERM|KILL|STOP|CONT>
# status <pid>
# pause|resume|stop|kill <pid>
set -u
ACTION="${1:-}"
STATE_DIR="${AUTOSTONE_STATE_DIR:-/tmp/autostone}"
mkdir -p "$STATE_DIR"

usage(){ echo "ERROR: usage: $0 start|status|signal|pause|resume|stop|kill ..."; exit 2; }

case "$ACTION" in
  start)
    [ "$#" -ge 8 ] || usage
    RUN_ID="$2"; JOB_ID="$3"; EXECUTION_ID="$4"; CALLBACK_URL="$5"; TIMEOUT="${6:-0}"; WORKDIR="${7:-}"; COMMAND="$8"; CALLBACK_SECRET_FILE="${9:-${AUTOSTONE_CALLBACK_SECRET_FILE:-/etc/autostone/callback-secret}}"
    BASE="${RUN_ID}_${JOB_ID}_${EXECUTION_ID}"
    SUP_PID_FILE="$STATE_DIR/${BASE}.supervisor.pid"
    PID_FILE="$STATE_DIR/${BASE}.pid"
    LOG_FILE="$STATE_DIR/${BASE}.log"
    CONTROL_FILE="$STATE_DIR/${BASE}.control"
    CHILD_PID_FILE="$STATE_DIR/${BASE}.child.pid"
    STARTED_FILE="$STATE_DIR/${BASE}.started"

    if [ -n "$WORKDIR" ] && [ ! -d "$WORKDIR" ]; then echo "ERROR: working directory does not exist: $WORKDIR"; exit 3; fi

    # The supervisor owns the callback lifecycle but the actual command gets its own
    # process group. Operator STOP/KILL targets the command group, never the supervisor.
    # Therefore a control action cannot accidentally kill the callback supervisor.
    nohup setsid bash -c '
      RUN_ID="$1"; JOB_ID="$2"; EXECUTION_ID="$3"; CALLBACK_URL="$4"; TIMEOUT="$5"; WORKDIR="$6"; COMMAND="$7"; LOG_FILE="$8"; CALLBACK_SECRET_FILE="$9"; CONTROL_FILE="${10}"; CHILD_PID_FILE="${11}"; STARTED_FILE="${12}"
      if [ -n "$WORKDIR" ]; then cd "$WORKDIR" || exit 125; fi
      : > "$CONTROL_FILE"
      rm -f "$CHILD_PID_FILE"

      # Run the user command in a separate session/process group.
      if [ "$TIMEOUT" -gt 0 ] 2>/dev/null; then
        setsid timeout --signal=TERM --kill-after=10s "${TIMEOUT}s" bash -c "$COMMAND" > "$LOG_FILE" 2>&1 &
      else
        setsid bash -c "$COMMAND" > "$LOG_FILE" 2>&1 &
      fi
      CHILD_PID=$!
      printf "%s\n" "$CHILD_PID" > "$CHILD_PID_FILE"
      printf "%s\n" "$CHILD_PID" > "$STARTED_FILE"

      wait "$CHILD_PID"; RC=$?
      CONTROL=""
      [ -r "$CONTROL_FILE" ] && CONTROL="$(cat "$CONTROL_FILE" 2>/dev/null || true)"

      STATUS=""
      if [ "$CONTROL" = "STOP" ]; then STATUS="STOPPED"; elif [ "$CONTROL" = "KILL" ]; then STATUS="KILLED"; elif [ "$RC" -eq 124 ]; then STATUS="TIMEOUT"; elif [ "$RC" -eq 0 ]; then STATUS="SUCCESS"; else STATUS="FAILED"; fi
      printf "%s\n" "$RC" > "${LOG_FILE}.exit"

      CALLBACK_SECRET=""
      if [ -r "$CALLBACK_SECRET_FILE" ]; then CALLBACK_SECRET="$(cat "$CALLBACK_SECRET_FILE")"; fi
      JSON="{\"runId\":\"${RUN_ID}\",\"jobId\":${JOB_ID},\"executionId\":${EXECUTION_ID},\"exitCode\":${RC},\"status\":\"${STATUS}\"}"
      curl -sS -m 15 -X POST "$CALLBACK_URL" -H "Content-Type: application/json" -H "X-AutoStone-Callback-Secret: ${CALLBACK_SECRET}" -d "$JSON" >> "${LOG_FILE}.callback" 2>&1 || true
      rm -f "$CHILD_PID_FILE"
    ' _ "$RUN_ID" "$JOB_ID" "$EXECUTION_ID" "$CALLBACK_URL" "$TIMEOUT" "$WORKDIR" "$COMMAND" "$LOG_FILE" "$CALLBACK_SECRET_FILE" "$CONTROL_FILE" "$CHILD_PID_FILE" "$STARTED_FILE" > /dev/null 2>&1 &

    SUP_PID=$!
    # Wait for a durable start handshake. This also works for commands that finish
    # immediately, because the STARTED file is not removed when the child exits.
    for _ in $(seq 1 100); do [ -s "$STARTED_FILE" ] && break; sleep 0.02; done
    if [ ! -s "$STARTED_FILE" ]; then echo "ERROR: wrapper could not start command"; kill "$SUP_PID" 2>/dev/null || true; exit 4; fi
    CHILD_PID="$(cat "$STARTED_FILE")"
    printf '%s\n' "$SUP_PID" > "$SUP_PID_FILE"
    printf '%s\n' "$CHILD_PID" > "$PID_FILE"
    echo "PID:$CHILD_PID"
    echo "LOG:$LOG_FILE"
    ;;

  status)
    PID="$2"
    if ! kill -0 "$PID" 2>/dev/null; then echo "STATUS:EXITED"; echo "PID:$PID"; exit 0; fi
    STAT="$(ps -o stat= -p "$PID" 2>/dev/null | tr -d ' ' || true)"
    case "$STAT" in T*) echo "STATUS:PAUSED";; Z*) echo "STATUS:EXITED";; *) echo "STATUS:RUNNING";; esac
    echo "PID:$PID"
    ;;

  pause|resume|stop|kill)
    PID="$2"
    case "$ACTION" in pause) SIGNAL_NAME="STOP";; resume) SIGNAL_NAME="CONT";; stop) SIGNAL_NAME="TERM";; kill) SIGNAL_NAME="KILL";; esac
    if ! kill -0 "$PID" 2>/dev/null; then echo "ERROR: no such process $PID"; exit 1; fi
    # Find the execution control marker associated with this command PID.
    CONTROL_FILE="$(for f in "$STATE_DIR"/*.pid; do [ -f "$f" ] || continue; case "$f" in *.supervisor.pid|*.child.pid) continue;; esac; grep -qx "$PID" "$f" 2>/dev/null && { echo "${f%.pid}.control"; break; }; done)"
    if [ "$ACTION" = "stop" ] && [ -n "$CONTROL_FILE" ]; then printf 'STOP\n' > "$CONTROL_FILE"; fi
    if [ "$ACTION" = "kill" ] && [ -n "$CONTROL_FILE" ]; then printf 'KILL\n' > "$CONTROL_FILE"; fi
    kill -s "$SIGNAL_NAME" -- "-$PID" 2>/dev/null || kill -s "$SIGNAL_NAME" "$PID"
    echo "OK:${SIGNAL_NAME}:${PID}"
    ;;

  signal)
    PID="$2"; SIGNAL_NAME="${3:-}"
    case "$SIGNAL_NAME" in TERM|KILL|STOP|CONT) ;; *) echo "ERROR: invalid signal $SIGNAL_NAME"; exit 2;; esac
    if ! kill -0 "$PID" 2>/dev/null; then echo "ERROR: no such process $PID"; exit 1; fi
    CONTROL_FILE="$(for f in "$STATE_DIR"/*.pid; do [ -f "$f" ] || continue; case "$f" in *.supervisor.pid|*.child.pid) continue;; esac; grep -qx "$PID" "$f" 2>/dev/null && { echo "${f%.pid}.control"; break; }; done)"
    [ "$SIGNAL_NAME" = "TERM" ] && [ -n "$CONTROL_FILE" ] && printf 'STOP\n' > "$CONTROL_FILE"
    [ "$SIGNAL_NAME" = "KILL" ] && [ -n "$CONTROL_FILE" ] && printf 'KILL\n' > "$CONTROL_FILE"
    kill -s "$SIGNAL_NAME" -- "-$PID" 2>/dev/null || kill -s "$SIGNAL_NAME" "$PID"
    echo "OK:${SIGNAL_NAME}:${PID}"
    ;;

  *) usage;;
esac
