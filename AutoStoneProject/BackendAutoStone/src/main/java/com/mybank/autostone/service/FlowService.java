package com.mybank.autostone.service;

import com.mybank.autostone.dto.*;
import com.mybank.autostone.entity.Job;
import com.mybank.autostone.entity.JobDependency;
import com.mybank.autostone.entity.JobExecution;
import com.mybank.autostone.entity.JobType;
import com.mybank.autostone.repository.JobDependencyRepository;
import com.mybank.autostone.repository.JobExecutionRepository;
import com.mybank.autostone.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.stream.Collectors;

/** Returns a graph scoped to the currently opened BOX. Descendants are never leaked into the view. */
@Service
@RequiredArgsConstructor
public class FlowService {
    private final JobRepository jobRepository;
    private final JobDependencyRepository dependencyRepository;
    private final JobExecutionRepository executionRepository;
    private final ExecutionStateService stateService;

    @Transactional(readOnly = true)
    public FlowResponse getRootFlow(String mode, int depth) {
        String safeMode = mode == null || mode.isBlank() ? "BOX" : mode.toUpperCase(Locale.ROOT);
        int safeDepth = Math.max(1, Math.min(depth <= 0 ? 1 : depth, 50));
        // IMPORTANT: a top-level JOB (one that was never placed inside a BOX, e.g. via
        // JIL import with no box_name, or created in the UI without selecting a parent
        // BOX) is still a root of the workflow forest and must be shown here. Filtering
        // this list down to JobType.BOX only was hiding every standalone JOB from the
        // main flow/dashboard graph even though the row existed in the database.
        List<Job> roots = jobRepository.findByBoxIsNull(Pageable.unpaged()).getContent().stream()
                .sorted(Comparator.comparing(Job::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Set<Long> ids = roots.stream().map(Job::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, JobExecution> latest = ids.isEmpty() ? Map.of() : executionRepository.findLatestForJobIds(new ArrayList<>(ids)).stream()
                .collect(Collectors.toMap(e -> e.getJob().getId(), e -> e, (a, b) -> a));
        List<FlowNodeResponse> nodes = roots.stream().map(j -> node(j, latest.get(j.getId()), 0)).toList();
        return FlowResponse.builder().rootJobId(null).rootJobName("ROOT").mode(safeMode).depth(safeDepth).nodes(nodes).edges(List.of()).build();
    }

    @Transactional(readOnly = true)
    public FlowResponse getFlow(Long rootId, String mode, int depth) {
        Job root = jobRepository.findById(rootId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + rootId));
        String safeMode = mode == null || mode.isBlank() ? "BOX" : mode.toUpperCase(Locale.ROOT);
        int safeDepth = Math.max(1, Math.min(depth <= 0 ? 10 : depth, 50));

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (root.getJobType() == JobType.BOX) {
            // BOX view is deliberately one level deep. Opening a nested BOX causes this
            // endpoint to be called again with that nested BOX id.
            jobRepository.findByBoxId(root.getId()).forEach(child -> ids.add(child.getId()));
        } else {
            ids.add(root.getId());
            // For a selected JOB, show only its direct dependency neighbors, never the
            // complete containing BOX subtree.
            dependencyRepository.findByJobId(root.getId()).forEach(d -> ids.add(d.getDependsOnJob().getId()));
            dependencyRepository.findByDependsOnJobId(root.getId()).forEach(d -> ids.add(d.getJob().getId()));
        }

        List<Job> jobs = ids.isEmpty() ? List.of() : jobRepository.findAllById(ids);
        // Keep an opened BOX tied to its current workflow run instead of showing a
        // globally-latest execution from another run/concurrent invocation.
        JobExecution rootExecution = executionRepository.findFirstByJobIdOrderByIdDesc(root.getId()).orElse(null);
        UUID workflowRunId = rootExecution == null ? null : rootExecution.getWorkflowRunId();
        List<JobExecution> latestExecutions = jobs.isEmpty() ? List.of()
                : workflowRunId == null
                    ? executionRepository.findLatestForJobIds(ids.stream().toList())
                    : executionRepository.findLatestForJobIdsAndWorkflowRunId(ids.stream().toList(), workflowRunId);
        Map<Long, JobExecution> latest = latestExecutions.stream()
                .collect(Collectors.toMap(e -> e.getJob().getId(), e -> e, (a, b) -> a));

        List<FlowEdgeResponse> edges = new ArrayList<>();
        Set<String> edgeKeys = new HashSet<>();
        for (Long id : ids) {
            for (JobDependency d : dependencyRepository.findByJobId(id)) {
                Long from = d.getDependsOnJob().getId();
                Long to = d.getJob().getId();
                if (!ids.contains(from) || !ids.contains(to)) continue;
                String key = from + ":" + to + ":" + d.getConditionType();
                if (edgeKeys.add(key)) edges.add(FlowEdgeResponse.builder().from(from).to(to)
                        .conditionType(d.getConditionType()).exitCodeValue(d.getExitCodeValue()).rawExpression(d.getRawExpression()).build());
            }
        }

        Map<Long, Integer> levels = calculateLevels(ids, edges);
        List<FlowNodeResponse> nodes = jobs.stream().sorted(Comparator.comparing(Job::getName, String.CASE_INSENSITIVE_ORDER)).map(j -> node(j, latest.get(j.getId()), levels.getOrDefault(j.getId(), 0))).toList();

        return FlowResponse.builder().rootJobId(root.getId()).rootJobName(root.getName())
                .mode(safeMode).depth(safeDepth).nodes(nodes).edges(edges).build();
    }

    private FlowNodeResponse node(Job j, JobExecution e, int level) {
        Integer childCount = j.getJobType() == JobType.BOX ? jobRepository.findByBoxId(j.getId()).size() : 0;
        String status = e == null ? "CREATED"
                : j.getJobType() == JobType.BOX ? stateService.deriveBoxStatus(j, e.getWorkflowRunId()).name() : e.getStatus().name();
        return FlowNodeResponse.builder()
                .id(j.getId()).type(j.getJobType() == JobType.BOX ? "BOX" : "JOB").name(j.getName()).jobType(j.getJobType().name())
                .boxId(j.getBox() == null ? null : j.getBox().getId())
                .parentBoxId(j.getBox() == null ? null : j.getBox().getId())
                .hostName(j.getHostName()).status(status)
                .executionId(e == null ? null : e.getId()).runId(e == null ? null : e.getRunId())
                .triggerType(j.getTriggerType() == null ? null : j.getTriggerType().name())
                .enabled(j.getEnabled()).hold(j.getHold()).ice(j.getIce()).noExecution(j.getNoExecution())
                .runtimeHold(e != null && Boolean.TRUE.equals(e.getRuntimeHold())).version(j.getVersion()).level(level)
                .childCount(childCount).hasChildren(childCount > 0).build();
    }

    private Map<Long,Integer> calculateLevels(Set<Long> ids, List<FlowEdgeResponse> edges) {
        Map<Long,List<Long>> parents = new HashMap<>();
        ids.forEach(id -> parents.put(id, new ArrayList<>()));
        for (FlowEdgeResponse e : edges) parents.computeIfAbsent(e.getTo(), k -> new ArrayList<>()).add(e.getFrom());
        Map<Long,Integer> cache = new HashMap<>();
        for (Long id : ids) level(id, parents, cache, new HashSet<>());
        return cache;
    }

    private int level(Long id, Map<Long,List<Long>> parents, Map<Long,Integer> cache, Set<Long> stack) {
        if (cache.containsKey(id)) return cache.get(id);
        if (!stack.add(id)) return 0;
        int value = parents.getOrDefault(id, List.of()).isEmpty() ? 0 : 1 + parents.get(id).stream()
                .mapToInt(p -> level(p, parents, cache, stack)).max().orElse(0);
        stack.remove(id); cache.put(id, value); return value;
    }
}
