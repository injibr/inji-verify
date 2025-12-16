package io.inji.verify.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.shaded.gson.Gson;
import io.inji.verify.dto.authorizationrequest.VPRequestStatusDto;
import io.inji.verify.dto.submission.PresentationSubmissionDto;
import io.inji.verify.dto.submission.VPSubmissionDto;
import io.inji.verify.dto.submission.VPTokenResultDto;
import io.inji.verify.enums.ErrorCode;
import io.inji.verify.enums.VPResultStatus;
import io.inji.verify.exception.VPSubmissionNotFoundException;
import io.inji.verify.exception.VpRequestNotFoundException;
import io.inji.verify.models.VpRequest;
import io.inji.verify.services.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class VPProcessServiceImpl implements VPProcessService{

    private final VerifiablePresentationRequestService verifiablePresentationRequestService;
    private final VerifiablePresentationSubmissionService verifiablePresentationSubmissionService;
    private final Gson gson;
    private final BankWebhookService bankWebhookService;
    private final PdfService pdfService;
    private final VpRequestService vpRequestService;


    /**
     * Validates and parses the presentationSubmission JSON.
     * Throws IllegalArgumentException if validation fails.
     */
    public PresentationSubmissionDto validatePresentationSubmission(String presentationSubmission) {
        PresentationSubmissionDto dto = gson.fromJson(presentationSubmission, PresentationSubmissionDto.class);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<PresentationSubmissionDto>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
        return dto;
    }

    /**
     * Ensures that the current VP request status exists.
     * Throws VpRequestNotFoundException if not found.
     */
    public void verifyCurrentRequestStatus(String state) {
        VPRequestStatusDto status = verifiablePresentationRequestService.getCurrentRequestStatus(state);
        if (Objects.isNull(status)) {
            throw new VpRequestNotFoundException("No VP Request found for state: " + state, ErrorCode.NO_VP_REQUEST);
        }
    }

    /**
     * Persists VP submission data.
     */
    public void submitVP(VPSubmissionDto vpSubmissionDto) {
        verifiablePresentationSubmissionService.submit(vpSubmissionDto);
    }

    @Override
    public boolean isCredentialEmpty(VPSubmissionDto vpSubmissionDto) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(vpSubmissionDto.getVpToken());

            JsonNode vcArray = root.get("verifiableCredential");

            return vcArray == null || !vcArray.isArray() || vcArray.size() == 0;

        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON", e);
        }
    }

    /**
     * Fetches the VpRequest object by requestId (state).
     * Throws VpRequestNotFoundException if missing.
     */
    public VpRequest fetchVpRequest(String requestId) {
        log.info("Fetching VpRequest for requestId: {}", requestId);
        VpRequest vpRequest = vpRequestService.getVpRequestsByRequestId(requestId);
        if (Objects.isNull(vpRequest)) {
            throw new VpRequestNotFoundException("No VP Request found for requestId: " + requestId, ErrorCode.NO_VP_REQUEST);
        }
        if (vpRequest.getBankCredential() == null || vpRequest.getBankCredential().getBankWebhookUrl() == null) {
            throw new VpRequestNotFoundException("BankCredential or Webhook URL missing for requestId: " + requestId, ErrorCode.INVALID_TRANSACTION_ID);
        }
        return vpRequest;
    }

    /**
     * Retrieves VP Token result from submission service.
     */
    public VPTokenResultDto retrieveVpResult(VpRequest vpRequest) {
        List<String> requestIds = verifiablePresentationRequestService.getLatestRequestIdFor(vpRequest.getTransactionId());
        if (requestIds.isEmpty()) {
            throw new VpRequestNotFoundException("Invalid transactionId for VP request: " + vpRequest.getTransactionId(), ErrorCode.INVALID_TRANSACTION_ID);
        }

        try {
            VPTokenResultDto result = verifiablePresentationSubmissionService.getVPResult(requestIds, vpRequest.getTransactionId());
            if (result.getVpResultStatus() == VPResultStatus.FAILED) {
                throw new VpRequestNotFoundException("VP submission failed", ErrorCode.NO_VP_SUBMISSION);
            }
            return result;
        } catch (VPSubmissionNotFoundException e) {
            log.error("VP submission not found: {}", e.getMessage());
            throw new VpRequestNotFoundException("VP Submission not found", ErrorCode.NO_VP_SUBMISSION);
        }
    }

    /**
     * Generates PDFs from VP token.
     */
    public Map<String, ByteArrayInputStream> generatePdf(String vpToken) {
        return pdfService.generatePdf(vpToken);
    }

    /**
     * Calls the bank webhook with generated PDFs and VP result.
     * Throws BankWebHookException if webhook fails.
     */
    public void callWebhook(Map<String, ByteArrayInputStream> pdfs,
                            VPTokenResultDto result,
                            String webhookUrl,
                            String apiKey,
                            String tokenUrl,
                            String tokenUri,
                            String webhookUri) {
        bankWebhookService.callWebhook(pdfs, result, webhookUrl,apiKey, tokenUrl, tokenUri, webhookUri);
    }
}