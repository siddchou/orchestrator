package com.novakai.orchestrator.api.controller;

// @author Siddhant Choudhary

import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.api.dto.CredentialRequest;
import com.novakai.orchestrator.api.dto.CredentialSummary;
import com.novakai.orchestrator.api.dto.KeyGenerationRequest;
import com.novakai.orchestrator.api.dto.KeyGenerationResponse;
import com.novakai.orchestrator.domain.entity.JobCredential;
import com.novakai.orchestrator.domain.enums.CredentialType;
import com.novakai.orchestrator.engine.service.CredentialDecryptionService;
import com.novakai.orchestrator.engine.service.KeyGeneratorService;
import com.novakai.orchestrator.repository.JobCredentialRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/credentials")
@Tag(name = "Credentials", description = "Encrypted credential management (ADMIN only)")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class CredentialController {

    private final JobCredentialRepository credRepo;
    private final CredentialDecryptionService cryptoService;
    private final KeyGeneratorService keyGeneratorService;

    public CredentialController(JobCredentialRepository credRepo,
                                CredentialDecryptionService cryptoService,
                                KeyGeneratorService keyGeneratorService) {
        this.credRepo = credRepo;
        this.cryptoService = cryptoService;
        this.keyGeneratorService = keyGeneratorService;
    }

    @Operation(summary = "List credential summaries (no secrets)")
    @GetMapping
    public ApiResponse<List<CredentialSummary>> list() {
        return ApiResponse.success(credRepo.findAll().stream()
                .map(c -> new CredentialSummary(c.getCredentialId(), c.getCredentialRef(), c.getCredType()))
                .toList());
    }

    @Operation(summary = "Create an encrypted credential")
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
        log.info("Created credential ref={} type={}", request.ref(), credType);
        return ApiResponse.success(new CredentialSummary(
                cred.getCredentialId(), cred.getCredentialRef(), cred.getCredType()));
    }

    @Operation(summary = "Generate SSH key pair and store public key")
    @PostMapping("/generate-keys")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KeyGenerationResponse> generateKeys(@Valid @RequestBody KeyGenerationRequest request) throws Exception {
        KeyGeneratorService.KeyGenerationResult result = keyGeneratorService.generateKeyPair(request);

        // Store the public key as an SSH_KEY credential
        JobCredential cred = JobCredential.builder()
                .credentialRef(request.ref())
                .credType(CredentialType.SSH_KEY)
                .credValue(cryptoService.encrypt(result.publicKey()))
                .createdAt(LocalDateTime.now())
                .build();
        cred = credRepo.save(cred);

        log.info("Generated SSH key pair ref={} algorithm={}", request.ref(), result.algorithm());

        return ApiResponse.success(new KeyGenerationResponse(
                result.privateKey(),
                result.publicKey(),
                result.fingerprint(),
                result.algorithm()));
    }

    @Operation(summary = "Delete a credential by ID")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        JobCredential cred = credRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Credential not found: " + id));
        credRepo.delete(cred);
        log.info("Deleted credential id={} ref={}", id, cred.getCredentialRef());
        return ApiResponse.success(null);
    }
}
