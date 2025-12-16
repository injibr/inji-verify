package io.inji.verify.services;

import io.inji.verify.dto.submission.PresentationSubmissionDto;
import io.inji.verify.dto.submission.VPSubmissionDto;
import io.inji.verify.dto.submission.VPTokenResultDto;
import io.inji.verify.models.VpRequest;

import java.io.ByteArrayInputStream;
import java.util.Map;

public interface VPProcessService {

    /**
     * Validates and parses the presentationSubmission JSON.
     *
     * @param presentationSubmission JSON string of the VP submission
     * @return Validated PresentationSubmissionDto
     */
    PresentationSubmissionDto validatePresentationSubmission(String presentationSubmission);

    /**
     * Ensures that the current VP request status exists.
     *
     * @param state unique state value
     */
    void verifyCurrentRequestStatus(String state);

    /**
     * Persists VP submission data.
     *
     * @param vpSubmissionDto DTO containing submission data
     */
    void submitVP(VPSubmissionDto vpSubmissionDto);

    /**
     * Fetches the VpRequest object by requestId (state).
     *
     * @param requestId unique requestId
     * @return VpRequest entity
     */
    VpRequest fetchVpRequest(String requestId);

    /**
     * Retrieves VP Token result from submission service.
     *
     * @param vpRequest VP Request entity
     * @return VPTokenResultDto with the result
     */
    VPTokenResultDto retrieveVpResult(VpRequest vpRequest);

    /**
     * Generates PDFs from VP token.
     *
     * @param vpToken VP Token string
     * @return Map of filenames to generated PDF streams
     */
    Map<String, ByteArrayInputStream> generatePdf(String vpToken);

    /**
     * Calls the bank webhook with generated PDFs and VP result.
     *
     * @param pdfs       Map of PDF files
     * @param result     VP token result DTO
     * @param webhookUrl URL to call
     */
    void callWebhook(Map<String, ByteArrayInputStream> pdfs,
                     VPTokenResultDto result,
                     String webhookUrl,
                     String apiKey,
                     String tokenUrl,
                     String tokenUri,
                     String webhookUri);
    /**
     * Checks if the VP submission contains any credentials.
     *
     * @param vpSubmissionDto DTO containing submission data
     * @return true if no credentials are present, false otherwise
     */
    boolean isCredentialEmpty(VPSubmissionDto vpSubmissionDto);
}
