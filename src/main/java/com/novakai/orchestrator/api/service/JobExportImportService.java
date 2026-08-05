package com.novakai.orchestrator.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.novakai.orchestrator.api.dto.*;
import com.novakai.orchestrator.domain.entity.*;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepExecutor;
import com.novakai.orchestrator.engine.spi.StepExecutorRegistry;
import com.novakai.orchestrator.notification.entity.NotificationSubscription;
import com.novakai.orchestrator.notification.repository.NotificationSubscriptionRepository;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobStepDependencyRepository;
import com.novakai.orchestrator.repository.JobCredentialRepository;
import static com.novakai.orchestrator.domain.entity.JobStepDependency.EdgeCondition;
import com.novakai.orchestrator.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles exporting job definitions to portable YAML/JSON format,
 * and importing with validation against registered step types and credential refs.
 */
@Service
public class JobExportImportService {

    private static final String FORMAT_VERSION = "1.0";

    private final JobDefinitionRepository jobRepo;
    private final JobStepDependencyRepository depRepo;
    private final JobCredentialRepository credRepo;
    private final TeamRepository teamRepo;
    private final StepExecutorRegistry registry;
    private final NotificationSubscriptionRepository subscriptionRepo;
    private final ObjectMapper jsonMapper;
    private final YAMLMapper yamlMapper;

    public JobExportImportService(JobDefinitionRepository jobRepo,
                                   JobStepDependencyRepository depRepo,
                                   JobCredentialRepository credRepo,
                                   TeamRepository teamRepo,
                                   StepExecutorRegistry registry,
                                   NotificationSubscriptionRepository subscriptionRepo) {
        this.jobRepo = jobRepo;
        this.depRepo = depRepo;
        this.credRepo = credRepo;
        this.teamRepo = teamRepo;
        this.registry = registry;
        this.subscriptionRepo = subscriptionRepo;

        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);

        this.yamlMapper = new YAMLMapper();
        this.yamlMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    // =====================================================================
    //  EXPORT
    // =====================================================================

    /** Export a job definition to JSON string */
    @Transactional(readOnly = true)
    public String exportToJson(Long jobId) {
        JobExport export = buildExport(jobId);
        try {
            return jsonMapper.writeValueAsString(export);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize job export", e);
        }
    }

    /** Export a job definition to YAML string */
    @Transactional(readOnly = true)
    public String exportToYaml(Long jobId) {
        JobExport export = buildExport(jobId);
        try {
            return yamlMapper.writeValueAsString(export);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize job export", e);
        }
    }

    /** Export a specific version of a job definition from stored JSON */
    public String exportVersionToJson(String exportedJson, String formatVersionOverride) {
        // Re-wrap with updated metadata if needed
        try {
            Map<String, Object> map = jsonMapper.readValue(exportedJson, Map.class);
            if (formatVersionOverride != null) {
                map.put("formatVersion", formatVersionOverride);
            }
            map.put("re_exported_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
            return jsonMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to re-export version", e);
        }
    }

    private JobExport buildExport(Long jobId) {
        JobDefinition job = jobRepo.findByIdWithSteps(jobId)
                .orElseThrow(() -> new com.novakai.orchestrator.engine.exception.JobNotFoundException(
                        "Job " + jobId));

        List<ExportStep> steps = new ArrayList<>();
        List<ExportDependency> dependencies = new ArrayList<>();

        for (JobStep step : job.getSteps()) {
            steps.add(new ExportStep(
                    step.getStepName(),
                    step.getStepOrder(),
                    step.getStepType(),
                    parseStepConfig(step.getStepConfig()),
                    "Y".equals(step.getContinueOnFailure()),
                    "Y".equals(step.getEnabled())
            ));

            // Load dependencies for this step
            for (JobStepDependency dep : depRepo.findByStep_StepId(step.getStepId())) {
                dependencies.add(new ExportDependency(
                        step.getStepName(),
                        dep.getDependsOnStep().getStepName(),
                        dep.getEdgeCondition().name()
                ));
            }
        }

        List<ExportEnvVar> envVars = job.getEnvVars().stream()
                .map(ev -> new ExportEnvVar(ev.getVarName(), ev.getVarValue(), "Y".equals(ev.getIsGlobal())))
                .collect(Collectors.toList());

        List<ExportNotificationSubscription> subscriptions = subscriptionRepo.findByJobId(jobId).stream()
                .map(sub -> {
                    List<String> eventsList = sub.getEvents() != null && !sub.getEvents().isBlank()
                            ? Arrays.asList(sub.getEvents().split(",")) : Collections.emptyList();
                    Map<String, Object> configMap = parseConfigJson(sub.getConfigJson());
                    return new ExportNotificationSubscription(
                            sub.getChannelType(),
                            eventsList,
                            configMap,
                            sub.isActive()
                    );
                })
                .collect(Collectors.toList());

        ExportSchedule schedule = null;
        if (job.getSchedule() != null) {
            JobSchedule js = job.getSchedule();
            schedule = new ExportSchedule(js.getCronExpression(), "Y".equals(js.getEnabled()));
        }

        return new JobExport(
                FORMAT_VERSION,
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "orchestrator-0.0.1-SNAPSHOT",
                job.getJobId().toString(),
                job.getJobName(),
                job.getDescription(),
                job.getWorkingDir(),
                job.getJavaHome(),
                parseClasspath(job.getClasspath()),
                "Y".equals(job.getEnabled()),
                job.getTeam() != null ? job.getTeam().getTeamName() : null,
                steps,
                dependencies,
                envVars,
                subscriptions,
                schedule,
                null // metadata reserved for future use
        );
    }

    /** Parse stepConfig CLOB back to a structured object (Map or null) */
    private Object parseStepConfig(String stepConfig) {
        if (stepConfig == null || stepConfig.isBlank()) return null;
        try {
            return jsonMapper.readValue(stepConfig, Object.class);
        } catch (JsonProcessingException e) {
            // Return as-is if not valid JSON
            return stepConfig;
        }
    }

    /** Parse classpath JSON array to List<String> */
    private List<String> parseClasspath(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return jsonMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    // =====================================================================
    //  IMPORT VALIDATION
    // =====================================================================

    /** Validate an import request. Returns list of error messages (empty = valid). */
    public List<String> validateImport(JobImportRequest request, boolean jobExists) {
        return validateImport(request, jobExists, false);
    }

    /**
     * Validate an import request with optional step-type bypass.
     * @param request the import request to validate
     * @param jobExists whether a job with the same name already exists
     * @param skipStepTypeValidation when true, skips validation against registered step types
     *   (used for rollback — stored versions may reference step types no longer registered)
     */
    public List<String> validateImport(JobImportRequest request, boolean jobExists, boolean skipStepTypeValidation) {
        List<String> errors = new ArrayList<>();

        if (request.steps() == null || request.steps().isEmpty()) {
            errors.add("Job must have at least one step");
            return errors; // can't validate further without steps
        }

        // Validate format version — reject unknown future versions
        String fv = request.formatVersion();
        if (fv == null || !fv.equals(FORMAT_VERSION)) {
            errors.add("Unsupported format version '" + fv + "'; expected '" + FORMAT_VERSION + "'");
        }

        // Build name set for duplicate and dependency checks
        Set<String> stepNames = new HashSet<>();

        for (ImportStepDefinition step : request.steps()) {
            String path = "steps.'" + step.stepName() + "'";

            if (step.stepName() == null || step.stepName().isBlank()) {
                errors.add(path + ": stepName is required");
                continue;
            }

            if (!stepNames.add(step.stepName())) {
                errors.add(path + ": duplicate step name '" + step.stepName() + "'");
            }

            // Validate step type against registered types (unless skipped for rollback)
            if (step.stepType() != null && !step.stepType().isBlank()) {
                if (!skipStepTypeValidation) {
                    Set<String> registeredTypes = registry.registeredTypes();
                    if (!registeredTypes.contains(step.stepType())) {
                        errors.add(path + ": unknown step type '" + step.stepType() +
                                "'. Available: " + registeredTypes);
                    } else {
                        // Validate step config fields against schema
                        validateStepConfigAgainstSchema(step, path, errors);
                    }
                }
            } else {
                errors.add(path + ": stepType is required");
            }
        }

        // Validate dependencies reference existing step names
        if (request.dependencies() != null) {
            for (ImportDependencyDefinition dep : request.dependencies()) {
                String path = "deps.'" + dep.stepName() + "->" + dep.dependsOnStepName() + "'";
                if (!stepNames.contains(dep.stepName())) {
                    errors.add(path + ": step '" + dep.stepName() + "' not found in steps");
                }
                if (!stepNames.contains(dep.dependsOnStepName())) {
                    errors.add(path + ": dependency target '" + dep.dependsOnStepName() + "' not found");
                }
                // Validate edge condition
                if (dep.edgeCondition() != null && !dep.edgeCondition().isBlank()) {
                    try {
                        EdgeCondition.valueOf(dep.edgeCondition());
                    } catch (IllegalArgumentException e) {
                        errors.add(path + ": invalid edgeCondition '" + dep.edgeCondition() +
                                "'. Valid: ON_SUCCESS, ON_FAILURE, ALWAYS");
                    }
                }
            }
        }

        // Validate DAG — no cycles
        if (request.dependencies() != null && !errors.stream().anyMatch(e -> e.contains("->"))) {
            if (detectCycle(request)) {
                errors.add("Dependencies contain a circular reference");
            }
        }

        // Validate env var keys are unique
        if (request.envVars() != null) {
            Set<String> varKeys = new HashSet<>();
            for (ImportEnvVarDefinition ev : request.envVars()) {
                if (!varKeys.add(ev.key())) {
                    errors.add("envVars: duplicate key '" + ev.key() + "'");
                }
            }
        }

        // Validate cron expression if schedule present
        if (request.schedule() != null && request.schedule().cronExpression() != null) {
            if (!isValidCron(request.schedule().cronExpression())) {
                errors.add("schedule: invalid cron expression '" + request.schedule().cronExpression() + "'");
            }
        }

        return errors;
    }

    /** Validate step config fields against the registered schema */
    private void validateStepConfigAgainstSchema(ImportStepDefinition step, String path, List<String> errors) {
        if (step.stepConfig() == null) return;

        Optional<StepConfigSchema> schemaOpt = registry.get(step.stepType()).map(StepExecutor::getConfigSchema);
        if (schemaOpt.isEmpty()) return;

        StepConfigSchema schema = schemaOpt.get();
        Map<?, ?> configMap;
        try {
            // If stepConfig is already a Map (from deserialized record), use it directly
            if (step.stepConfig() instanceof Map) {
                configMap = (Map<?, ?>) step.stepConfig();
            } else {
                // Try to deserialize from JSON string or other representation
                String json;
                if (step.stepConfig() instanceof String) {
                    json = (String) step.stepConfig();
                } else {
                    json = jsonMapper.writeValueAsString(step.stepConfig());
                }
                configMap = jsonMapper.readValue(json, Map.class);
            }
        } catch (Exception e) {
            errors.add(path + ": stepConfig is not valid JSON");
            return;
        }

        // Check for required fields and secret refs
        for (FieldDefinition field : schema.fields()) {
            if (field.required() && !configMap.containsKey(field.name())) {
                errors.add(path + ": missing required config field '" + field.name() + "'");
            }

            // Validate SECRET_REF values exist as credentials
            if (field.type() == FieldType.SECRET_REF) {
                Object val = configMap.get(field.name());
                if (val != null && !isCredentialResolved(val.toString())) {
                    errors.add(path + ": credential ref '" + val + "' not found");
                }
            }
        }
    }

    /** Check if a credential reference exists in the database */
    private boolean isCredentialResolved(String credentialRef) {
        return credRepo.findByCredentialRef(credentialRef).isPresent();
    }

    /** Simple cycle detection using DFS on import dependency graph */
    private boolean detectCycle(JobImportRequest request) {
        Set<String> stepNames = request.steps().stream()
                .map(ImportStepDefinition::stepName)
                .collect(Collectors.toSet());

        Map<String, List<String>> adj = new HashMap<>();
        for (String name : stepNames) {
            adj.put(name, new ArrayList<>());
        }

        if (request.dependencies() != null) {
            for (ImportDependencyDefinition dep : request.dependencies()) {
                adj.computeIfAbsent(dep.dependsOnStepName(), k -> new ArrayList<>()).add(dep.stepName());
            }
        }

        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        for (String node : adj.keySet()) {
            if (!visited.contains(node)) {
                if (dfsCycle(node, adj, visited, recStack)) return true;
            }
        }
        return false;
    }

    private boolean dfsCycle(String node, Map<String, List<String>> adj, Set<String> visited, Set<String> recStack) {
        visited.add(node);
        recStack.add(node);
        for (String neighbor : adj.getOrDefault(node, Collections.emptyList())) {
            if (!visited.contains(neighbor)) {
                if (dfsCycle(neighbor, adj, visited, recStack)) return true;
            } else if (recStack.contains(neighbor)) {
                return true;
            }
        }
        recStack.remove(node);
        return false;
    }

    /** Basic cron expression validation (5-field standard or 6/7-field Quartz) */
    private boolean isValidCron(String cron) {
        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 5 || parts.length > 7) return false;
        // Each field must be non-empty and contain only valid cron chars (including ? for Quartz)
        return Arrays.stream(parts).allMatch(p -> p.matches("^[0-9*,\\-/]+$") || p.equals("?"));
    }

    // =====================================================================
    //  IMPORT EXECUTION
    // =====================================================================

    /**
     * Import a job definition. Creates new or updates existing based on mode.
     * Returns the imported JobDefinition.
     */
    @Transactional
    public JobDefinition importJob(JobImportRequest request, Long teamId) {
        EdgeCondition defaultEdgeCondition = EdgeCondition.ON_SUCCESS;

        // Check if job with same name exists
        Optional<JobDefinition> existingOpt = jobRepo.findByJobName(request.jobName());
        boolean exists = existingOpt.isPresent();

        JobDefinition job;
        if (exists) {
            JobImportRequest.Mode mode = request.modeEnum();
            return switch (mode) {
                case ERROR -> {
                    String msg = "Job '" + request.jobName() + "' already exists. " +
                            "Use mode=UPDATE to overwrite or mode=SKIP to ignore.";
                    throw new IllegalArgumentException(msg);
                }
                case SKIP -> existingOpt.get();
                case UPDATE -> {
                    JobDefinition existing = existingOpt.get();
                    // Update job-level fields
                    existing.setDescription(request.description());
                    existing.setWorkingDir(request.workingDir());
                    existing.setJavaHome(request.javaHome());
                    existing.setClasspath(serializeClasspath(request.classpathEntries()));
                    existing.setEnabled(request.enabled() != null && request.enabled() ? "Y" : "N");

                    // Replace steps — flush deletions before inserts to avoid unique constraint
                    // violation with IDENTITY columns (INSERT needs PK first, but old row blocks)
                    existing.getSteps().clear();
                    jobRepo.saveAndFlush(existing);

                    for (ImportStepDefinition stepDef : request.steps()) {
                        JobStep step = new JobStep();
                        step.setStepName(stepDef.stepName());
                        step.setStepOrder(stepDef.stepOrder() != null ? stepDef.stepOrder() : 0);
                        step.setStepType(stepDef.stepType());
                        step.setStepConfig(serializeStepConfig(stepDef.stepConfig()));
                        step.setContinueOnFailure(stepDef.continueOnFailure() != null && stepDef.continueOnFailure() ? "Y" : "N");
                        step.setEnabled(stepDef.enabled() != null && stepDef.enabled() ? "Y" : "N");
                        step.setJobDefinition(existing);
                        existing.getSteps().add(step);
                    }

                    // Replace env vars — same two-phase flush pattern
                    existing.getEnvVars().clear();
                    jobRepo.saveAndFlush(existing);

                    if (request.envVars() != null) {
                        for (ImportEnvVarDefinition evDef : request.envVars()) {
                            JobEnvVar ev = new JobEnvVar();
                            ev.setVarName(evDef.key());
                            ev.setVarValue(evDef.value());
                            ev.setIsGlobal(evDef.isGlobal() != null && evDef.isGlobal() ? "Y" : "N");
                            ev.setJobDefinition(existing);
                            existing.getEnvVars().add(ev);
                        }
                    }

                    // Flush to get step IDs, then create dependencies
                    existing = jobRepo.saveAndFlush(existing);
                    Map<String, JobStep> nameToStep = new HashMap<>();
                    for (JobStep s : existing.getSteps()) {
                        nameToStep.put(s.getStepName(), s);
                    }

                    if (request.dependencies() != null) {
                        for (ImportDependencyDefinition depDef : request.dependencies()) {
                            JobStep child = nameToStep.get(depDef.stepName());
                            JobStep parent = nameToStep.get(depDef.dependsOnStepName());
                            if (child == null || parent == null) continue;
                            EdgeCondition ec = EdgeCondition.ON_SUCCESS;
                            try { ec = EdgeCondition.valueOf(depDef.edgeCondition() != null ? depDef.edgeCondition() : "ON_SUCCESS"); } catch (IllegalArgumentException ignored) {}
                            depRepo.save(JobStepDependency.builder().step(child).dependsOnStep(parent).edgeCondition(ec).build());
                        }
                    }

                    // Update or create schedule
                    if (request.schedule() != null) {
                        Optional<JobSchedule> existingSched = existing.getSchedule() != null
                                ? java.util.Optional.of(existing.getSchedule()) : java.util.Optional.empty();
                        if (existingSched.isPresent()) {
                            JobSchedule sched = existingSched.get();
                            sched.setCronExpression(request.schedule().cronExpression());
                            sched.setEnabled(request.schedule().enabled() != null && request.schedule().enabled() ? "Y" : "N");
                            sched.setUpdatedAt(java.time.LocalDateTime.now());
                        } else {
                            JobSchedule sched = new JobSchedule();
                            sched.setCronExpression(request.schedule().cronExpression());
                            sched.setEnabled(request.schedule().enabled() != null && request.schedule().enabled() ? "Y" : "N");
                            sched.setJobDefinition(existing);
                            existing.setSchedule(sched);
                        }
                    }

                    // Replace subscriptions
                    subscriptionRepo.findByJobId(existing.getJobId()).forEach(sub -> {
                        sub.setActive(false);
                    });
                    subscriptionRepo.flush();

                    if (request.subscriptions() != null) {
                        for (ImportNotificationSubscriptionDefinition subDef : request.subscriptions()) {
                            NotificationSubscription sub = new NotificationSubscription();
                            sub.setJobId(existing.getJobId());
                            sub.setChannelType(subDef.channelType());
                            sub.setEvents(subDef.events() != null ? String.join(",", subDef.events()) : null);
                            sub.setConfigJson(serializeMap(subDef.config()));
                            sub.setActive(subDef.active() != null && subDef.active());
                            subscriptionRepo.save(sub);
                        }
                    }

                    yield jobRepo.save(existing);
                }
            };
        }

        // --- Create new job ---
        Team team = null;
        if (teamId != null) {
            team = teamRepo.findById(teamId)
                    .orElseThrow(() -> new RuntimeException("Team not found: " + teamId));
        }

        job = JobDefinition.builder()
                .jobName(request.jobName())
                .description(request.description())
                .workingDir(request.workingDir())
                .javaHome(request.javaHome())
                .classpath(serializeClasspath(request.classpathEntries()))
                .enabled(request.enabled() != null && request.enabled() ? "Y" : "N")
                .team(team)
                .build();

        // --- Create steps (need DB IDs for dependency resolution) ---
        Map<String, JobStep> stepNameToEntity = new HashMap<>();

        for (ImportStepDefinition stepDef : request.steps()) {
            JobStep step = new JobStep();
            step.setStepName(stepDef.stepName());
            step.setStepOrder(stepDef.stepOrder() != null ? stepDef.stepOrder() : 0);
            step.setStepType(stepDef.stepType());
            step.setStepConfig(serializeStepConfig(stepDef.stepConfig()));
            step.setContinueOnFailure(stepDef.continueOnFailure() != null && stepDef.continueOnFailure() ? "Y" : "N");
            step.setEnabled(stepDef.enabled() != null && stepDef.enabled() ? "Y" : "N");
            step.setJobDefinition(job);
            job.getSteps().add(step);
            // Step ID will be assigned on flush; we need it for dependencies
            stepNameToEntity.put(stepDef.stepName(), step);
        }

        // Flush to get IDs
        job = jobRepo.saveAndFlush(job);
        // Refresh step IDs from persisted entities
        for (JobStep step : job.getSteps()) {
            stepNameToEntity.put(step.getStepName(), step);
        }

        // --- Create env vars ---
        if (request.envVars() != null) {
            for (ImportEnvVarDefinition evDef : request.envVars()) {
                JobEnvVar ev = new JobEnvVar();
                ev.setVarName(evDef.key());
                ev.setVarValue(evDef.value());
                ev.setIsGlobal(evDef.isGlobal() != null && evDef.isGlobal() ? "Y" : "N");
                ev.setJobDefinition(job);
                job.getEnvVars().add(ev);
            }
        }

        // --- Create schedule ---
        if (request.schedule() != null) {
            JobSchedule sched = new JobSchedule();
            sched.setCronExpression(request.schedule().cronExpression());
            sched.setEnabled(request.schedule().enabled() != null && request.schedule().enabled() ? "Y" : "N");
            sched.setJobDefinition(job);
            job.setSchedule(sched);
        }

        // --- Create dependencies (resolve names to IDs) ---
        if (request.dependencies() != null) {
            for (ImportDependencyDefinition depDef : request.dependencies()) {
                JobStep child = stepNameToEntity.get(depDef.stepName());
                JobStep parent = stepNameToEntity.get(depDef.dependsOnStepName());
                if (child == null || parent == null) continue; // validation should have caught this

                EdgeCondition edgeCondition = defaultEdgeCondition;
                try {
                    edgeCondition = EdgeCondition.valueOf(
                            depDef.edgeCondition() != null ? depDef.edgeCondition() : "ON_SUCCESS");
                } catch (IllegalArgumentException e) {
                    // use default
                }

                JobStepDependency dep = JobStepDependency.builder()
                        .step(child)
                        .dependsOnStep(parent)
                        .edgeCondition(edgeCondition)
                        .build();
                depRepo.save(dep);
            }
        }

        // --- Create notification subscriptions ---
        if (request.subscriptions() != null) {
            for (ImportNotificationSubscriptionDefinition subDef : request.subscriptions()) {
                NotificationSubscription sub = new NotificationSubscription();
                sub.setJobId(job.getJobId());
                sub.setChannelType(subDef.channelType());
                sub.setEvents(subDef.events() != null ? String.join(",", subDef.events()) : null);
                sub.setConfigJson(serializeMap(subDef.config()));
                sub.setActive(subDef.active() != null && subDef.active());
                subscriptionRepo.save(sub);
            }
        }

        return jobRepo.save(job);
    }

    /** Serialize step config object to JSON string */
    private String serializeStepConfig(Object config) {
        if (config == null) return null;
        try {
            if (config instanceof String s && !s.isBlank()) {
                // Already a JSON string — validate it's parseable
                jsonMapper.readValue(s, Object.class);
                return s;
            }
            return jsonMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            if (config instanceof String) return (String) config;
            throw new RuntimeException("Failed to serialize stepConfig", e);
        }
    }

    /** Serialize classpath list to JSON string */
    private String serializeClasspath(List<String> entries) {
        if (entries == null || entries.isEmpty()) return null;
        try {
            return jsonMapper.writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize classpath", e);
        }
    }

    /** Parse config JSON string from subscription entity to Map */
    private Map<String, Object> parseConfigJson(String configJson) {
        if (configJson == null || configJson.isBlank()) return Collections.emptyMap();
        try {
            return jsonMapper.readValue(configJson, Map.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    /** Serialize a Map to JSON string for subscription config */
    private String serializeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return jsonMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize subscription config", e);
        }
    }
}
