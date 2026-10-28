from pathlib import Path
import re, sys
root=Path(__file__).resolve().parent
fail=[]

def must(cond,msg):
    if not cond: fail.append(msg)

api=(root/'AutoStoneFrontend_final/src/api/api.js').read_text()
ctl=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/controller/EventController.java').read_text()
event=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/service/EventService.java').read_text()
state=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/service/ExecutionStateService.java').read_text()
dep=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/service/DependencyResolverService.java').read_text()
coord=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/service/ExecutionCoordinatorService.java').read_text()
remote=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/remote/RemoteExecutionService.java').read_text()
wrap=(root/'BackendAutoStone/scripts/wrapper.sh').read_text()

# Frontend event API must have matching backend mappings.
front=set(re.findall(r'request\(`?(/event/[^`"\']+)', api))
front={re.sub(r'\$\{jobId\}', '{jobId}', p) for p in front}
backs=set('/event'+m for m in re.findall(r'@PostMapping\("([^"]+)"\)', ctl))
for p in front:
    must(p in backs, f'frontend endpoint missing in backend: {p}')

# Core operator actions.
for action in ['start','pause','resume','stop','kill','hold','off-hold','skip']:
    must(f'/event/{action}/' in api, f'frontend missing {action}')

# Skip must release downstream, and skipped predecessor must count as success for s(...).
must('releaseDownstreamAfterOperatorSkip' in event, 'skip does not trigger downstream release')
must('case SUCCESS, SKIPPED -> ConditionEvaluator.Outcome.SUCCESS' in dep, 'SKIPPED is not treated as resolved success dependency')

# HALTED must not be a normal RUNNING/PAUSED transition and must not be a wrapper signal.
run_line=next((x for x in state.splitlines() if 'm.put(JobStatus.RUNNING' in x), '')
pause_line=next((x for x in state.splitlines() if 'm.put(JobStatus.PAUSED' in x), '')
must('HALTED' not in run_line, 'RUNNING can still transition directly to HALTED')
must('HALTED' not in pause_line, 'PAUSED can still transition directly to HALTED')
must('HALT' not in wrap, 'wrapper still exposes HALT process action')

# HOLD must reject live processes.
must('Cannot HOLD a RUNNING/PAUSED execution' in event, 'HOLD does not reject RUNNING/PAUSED')

# Shared workflow runId for nested BOX children.
must('.runId(childRunId).workflowRunId(workflowRunId)' in coord and 'UUID childRunId = workflowRunId;' in coord,
     'nested child does not share workflow runId')

# Wrapper/backend process-control contract.
for sig in ['TERM','KILL','STOP','CONT']:
    must(sig in wrap, f'wrapper missing signal {sig}')
must('" signal " + pid + " " + signal.posixSignal()' in remote, 'backend wrapper signal contract mismatch')
must('X-AutoStone-Callback-Secret' in wrap and 'X-AutoStone-Callback-Secret' in (root/'BackendAutoStone/src/main/java/com/mybank/autostone/controller/JobCallbackController.java').read_text(), 'callback secret contract mismatch')

if fail:
    print('PRODUCTION SOURCE CONTRACT: FAIL')
    for x in fail: print('-',x)
    sys.exit(1)
print('PRODUCTION SOURCE CONTRACT: PASS')
print(f'Validated {len(front)} frontend /event endpoints and core execution invariants.')
