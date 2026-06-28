package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.api.dto.CredentialRequest;
import com.novakai.orchestrator.api.dto.CredentialSummary;
import com.novakai.orchestrator.domain.entity.JobCredential;
import com.novakai.orchestrator.domain.enums.CredentialType;
import com.novakai.orchestrator.engine.service.CredentialDecryptionService;
import com.novakai.orchestrator.repository.JobCredentialRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/credentials")
@PreAuthorize("hasRole('ADMIN')")
public class CredentialController {

    private final JobCredentialRepository credRepo;
    private final CredentialDecryptionService cryptoService;

    public CredentialController(JobCredentialRepository credRepo,
                                CredentialDecryptionService cryptoService) {
        this.credRepo = credRepo;
        this.cryptoService = cryptoService;
    }

    @GetMapping
    public ApiResponse<List<CredentialSummary>> list() {
        return ApiResponse.success(credRepo.findAll().stream()
                .map(c -> new CredentialSummary(c.getCredentialId(), c.getCredentialRef(), c.getCredType()))
                .toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CredentialSummary> create(@Valid @RequestBody CredentialRequest request) throws Exception {
        CredentialType credType;
        try {
            credType = CredentialType.valueOf(request.type());
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error("Invalid credential type: " + request.type());
        }

        String encrypted = cryptoService.encrypt(request.value());
        JobCredential cred = JobCredential.builder()
                .credentialRef(request.ref())
                .credType(credType)
                .credValue(encrypted)
                .createdAt(LocalDateTime.now())
                .build();
        cred = credRepo.save(cred);
        return ApiResponse.success(new CredentialSummary(
                cred.getCredentialId(), cred.getCredentialRef(), cred.getCredType()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        JobCredential cred = credRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Credential not found: " + id));
        credRepo.delete(cred);
    }
}
