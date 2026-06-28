package com.novakai.orchestrator.api.service;

import com.novakai.orchestrator.api.dto.*;
import com.novakai.orchestrator.api.mapper.JobDefinitionMapper;
import com.novakai.orchestrator.domain.entity.JobDefinition;
import com.novakai.orchestrator.domain.entity.JobEnvVar;
import com.novakai.orchestrator.domain.entity.JobSchedule;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.engine.JobSchedulerService;
import com.novakai.orchestrator.engine.exception.JobNotFoundException;
import com.novakai.orchestrator.repository.JobDefinitionRepository;
import com.novakai.orchestrator.repository.JobEnvVarRepository;
import com.novakai.orchestrator.repository.JobScheduleRepository;
import com.novakai.orchestrator.repository.JobStepRepository;
import com.novakai.orchestrator.security.Auditable;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobDefinitionService {

    private final JobDefinitionRepository jobRepo;
    private final JobStepRepository stepRepo;
    private final JobEnvVarRepository envVarRepo;
    private final JobScheduleRepository scheduleRepo;
    private final JobDefinitionMapper mapper;
    private final JobSchedulerService schedulerService;

    @Transactional(readOnly = true)
    public Page<JobDefinitionResponse> listJobs(String search, PageRequest pageRequest) {
        Page<JobDefinition> result;
        if (search != null && !search.isBlank()) {
            result = jobRepo.findByJobNameContainingIgnoreCase(search, pageRequest);
        } else {
            result = jobRepo.findAll(pageRequest);
        }
        return result.map(mapper::toResponse);
    }

    @Transactional
    @Auditable(action = "CREATE_JOB", entityType = "JOB")
    public JobDefinitionResponse createJob(JobDefinitionRequest request) {
        JobDefinition job = new JobDefinition();
        mapper.toEntity(request, job);
        job.setEnabled("Y");
        job = jobRepo.save(job);
        return mapper.toResponse(job);
    }

    @Transactional
    public JobDefinitionResponse updateJob(Long jobId, JobDefinitionRequest request) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        mapper.toEntity(request, job);
        job.setUpdatedAt(LocalDateTime.now());
        job = jobRepo.save(job);
        return mapper.toResponse(job);
    }

    @Transactional(readOnly = true)
    public JobDefinitionResponse getJob(Long jobId) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        return mapper.toResponse(job);
    }

    @Transactional
    @Auditable(action = "DELETE_JOB", entityType = "JOB")
    public void deleteJob(Long jobId) {
        if (!jobRepo.existsById(jobId)) {
            throw new JobNotFoundException(jobId);
        }
        jobRepo.deleteById(jobId);
    }

    @Transactional
    public JobDefinitionResponse toggleEnabled(Long jobId) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        job.setEnabled("Y".equals(job.getEnabled()) ? "N" : "Y");
        job = jobRepo.save(job);
        return mapper.toResponse(job);
    }

    // --- Steps ---

    @Transactional
    public JobStepResponse addStep(Long jobId, JobStepRequest request) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        JobStep step = mapper.toStepEntity(request, job);
        step = stepRepo.save(step);
        return mapper.toStepResponse(step);
    }

    @Transactional
    public JobStepResponse updateStep(Long jobId, Long stepId, JobStepRequest request) {
        JobStep step = stepRepo.findById(stepId)
                .orElseThrow(EntityNotFoundException::new);
        if (!step.getJobDefinition().getJobId().equals(jobId)) {
            throw new EntityNotFoundException("Step does not belong to job");
        }
        step.setStepName(request.stepName());
        step.setStepOrder(request.stepOrder());
        step.setStepType(request.stepType());
        step.setStepConfig(request.stepConfig());
        step.setContinueOnFailure(request.continueOnFailure() ? "Y" : "N");
        step.setEnabled(request.enabled() ? "Y" : "N");
        step = stepRepo.save(step);
        return mapper.toStepResponse(step);
    }

    @Transactional
    public void deleteStep(Long jobId, Long stepId) {
        JobStep step = stepRepo.findById(stepId)
                .orElseThrow(EntityNotFoundException::new);
        if (!step.getJobDefinition().getJobId().equals(jobId)) {
            throw new EntityNotFoundException("Step does not belong to job");
        }
        stepRepo.delete(step);
    }

    @Transactional
    public List<JobStepResponse> reorderSteps(Long jobId, List<Long> stepIds) {
        for (int i = 0; i < stepIds.size(); i++) {
            JobStep step = stepRepo.findById(stepIds.get(i))
                    .orElseThrow(EntityNotFoundException::new);
            if (!step.getJobDefinition().getJobId().equals(jobId)) {
                throw new EntityNotFoundException("Step does not belong to job");
            }
            step.setStepOrder(i + 1);
            stepRepo.save(step);
        }
        return stepRepo.findByJobDefinition_JobIdOrderByStepOrderAsc(jobId)
                .stream().map(mapper::toStepResponse).toList();
    }

    // --- Env Vars ---

    @Transactional(readOnly = true)
    public List<EnvVarResponse> listEnvVars(Long jobId) {
        jobRepo.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        return envVarRepo.findByJobDefinition_JobId(jobId)
                .stream().map(mapper::toEnvVarResponse).toList();
    }

    @Transactional
    public EnvVarResponse addEnvVar(Long jobId, EnvVarRequest request) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        JobEnvVar envVar = JobEnvVar.builder()
                .jobDefinition(job)
                .varName(request.key())
                .varValue(request.value())
                .isGlobal("N")
                .build();
        envVar = envVarRepo.save(envVar);
        return mapper.toEnvVarResponse(envVar);
    }

    @Transactional
    public void deleteEnvVar(Long jobId, Long envId) {
        JobEnvVar envVar = envVarRepo.findById(envId)
                .orElseThrow(EntityNotFoundException::new);
        if (envVar.getJobDefinition() != null && !envVar.getJobDefinition().getJobId().equals(jobId)) {
            throw new EntityNotFoundException("Env var does not belong to job");
        }
        envVarRepo.delete(envVar);
    }

    // --- Schedule ---

    @Transactional(readOnly = true)
    public JobScheduleResponse getSchedule(Long jobId) {
        JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Schedule not found"));
        return mapper.toScheduleResponse(schedule);
    }

    @Transactional
    public JobScheduleResponse createSchedule(Long jobId, JobScheduleRequest request) {
        JobDefinition job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        JobSchedule schedule = JobSchedule.builder()
                .jobDefinition(job)
                .cronExpression(request.cronExpression())
                .enabled("Y")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        schedule = scheduleRepo.save(schedule);
        schedulerService.register(schedule);
        return mapper.toScheduleResponse(schedule);
    }

    @Transactional
    public JobScheduleResponse updateSchedule(Long jobId, JobScheduleRequest request) {
        JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Schedule not found"));
        schedule.setCronExpression(request.cronExpression());
        schedule.setUpdatedAt(LocalDateTime.now());
        schedule = scheduleRepo.save(schedule);
        schedulerService.updateSchedule(schedule);
        return mapper.toScheduleResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(Long jobId) {
        JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Schedule not found"));
        schedulerService.cancel(schedule.getScheduleId());
        scheduleRepo.delete(schedule);
    }

    @Transactional
    public JobScheduleResponse toggleSchedule(Long jobId, boolean enable) {
        JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Schedule not found"));
        schedule.setEnabled(enable ? "Y" : "N");
        schedule.setUpdatedAt(LocalDateTime.now());
        schedule = scheduleRepo.save(schedule);
        if (enable) {
            schedulerService.register(schedule);
        } else {
            schedulerService.cancel(schedule.getScheduleId());
        }
        return mapper.toScheduleResponse(schedule);
    }
}