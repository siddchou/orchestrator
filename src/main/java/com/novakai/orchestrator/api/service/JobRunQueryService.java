package com.novakai.orchestrator.api.service;

// @author Siddhant Choudhary

import com.novakai.orchestrator.api.dto.JobRunDetail;
import com.novakai.orchestrator.api.dto.JobRunSummary;
import com.novakai.orchestrator.api.mapper.JobDefinitionMapper;
import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.entity.JobRunStep;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.repository.JobRunRepository;
import com.novakai.orchestrator.repository.JobRunStepRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobRunQueryService {

    private final JobRunRepository runRepo;
    private final JobRunStepRepository runStepRepo;
    private final JobDefinitionMapper mapper;

    @Transactional(readOnly = true)
    public Page<JobRunSummary> listRuns(Long jobId, RunStatus status,
                                        LocalDate from, LocalDate to,
                                        Pageable pageable) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.atTime(23, 59, 59, 999999999) : null;
        return runRepo.filterRuns(jobId, status, fromDt, toDt, pageable)
                .map(mapper::toRunSummary);
    }

    @Transactional(readOnly = true)
    public JobRunDetail getRunDetail(Long runId) {
        JobRun run = runRepo.findById(runId)
                .orElseThrow(() -> new EntityNotFoundException("Run not found: " + runId));
        List<JobRunStep> steps = runStepRepo.findByJobRun_RunIdOrderByStepOrderAsc(runId);
        return mapper.toRunDetail(run, steps);
    }

    @Transactional(readOnly = true)
    public String getStepLog(Long runId, Long stepId) {
        if (!runStepRepo.existsByRunStepIdAndJobRun_RunId(stepId, runId)) {
            throw new EntityNotFoundException("Step " + stepId + " not found in run " + runId);
        }
        JobRunStep runStep = runStepRepo.findById(stepId)
                .orElseThrow(() -> new EntityNotFoundException("Run step not found: " + stepId));
        return runStep.getLogOutput() != null ? runStep.getLogOutput() : "";
    }

    @Transactional(readOnly = true)
    public JobRunSummary toRunSummary(JobRun run) {
        return mapper.toRunSummary(run);
    }
}