package io.inji.verify.controller;

import io.inji.verify.dto.core.ErrorDto;
import io.inji.verify.dto.submission.PresentationSubmissionDto;
import io.inji.verify.dto.submission.VPSubmissionDto;
import io.inji.verify.dto.submission.VPTokenResultDto;
import io.inji.verify.exception.BankWebHookException;
import io.inji.verify.exception.VpRequestNotFoundException;
import io.inji.verify.models.VpRequest;
import io.inji.verify.services.VPProcessService;
import io.inji.verify.shared.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.util.Map;

/**
 * Controller for handling Verifiable Presentation (VP) submissions and processing.
 * <p>
 * This controller provides an endpoint to submit a VP along with its presentation submission,
 * validate the input, process the VP, trigger a webhook, and generate a PDF response.
 */
@RestController
@RequestMapping(path = Constants.RESPONSE_SUBMISSION_URI_ROOT)
@Slf4j
public class VPProcessController {
    private final VPProcessService vpProcessService;

    public VPProcessController(VPProcessService vpProcessService) {
        this.vpProcessService = vpProcessService;

    }

    /**
     * Handles the submission of a Verifiable Presentation (VP) via the `/vp-process` endpoint.
     * <p>
     * Accepts parameters in x-www-form-urlencoded format, validates the presentation submission,
     * processes the VP, triggers a webhook, and returns a generated PDF as a response.
     *
     * @param vpToken                the VP token as a String (required)
     * @param presentationSubmission the presentation submission as a JSON String (required)
     * @param state                  the state parameter to correlate the request (required)
     * @return a ResponseEntity containing the generated PDF or an error response
     */
    @PostMapping(path = "/vp-process", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> submitVP(
            @NotNull @NotBlank @RequestParam("vp_token") String vpToken,
            @NotNull @NotBlank @RequestParam("presentation_submission") String presentationSubmission,
            @NotNull @NotBlank @RequestParam("state") String state) {
        try {
            log.info("vp_token: {}", vpToken);
            log.info("presentation_submission: {}", presentationSubmission);
            log.info("state: {}", state);
            // Step 1: Validate presentation submission
            PresentationSubmissionDto dto = vpProcessService.validatePresentationSubmission(presentationSubmission);

            // Step 2: Verify request status
            vpProcessService.verifyCurrentRequestStatus(state);

            // Step 3: Submit VP
            vpProcessService.submitVP(new VPSubmissionDto(vpToken, dto, state));

            // Step 4: Fetch VpRequest
            VpRequest vpRequest = vpProcessService.fetchVpRequest(state);

            // Step 5: Retrieve VP result
            VPTokenResultDto result = vpProcessService.retrieveVpResult(vpRequest);

            // Step 6: Generate PDF
            Map<String, ByteArrayInputStream> pdfs = vpProcessService.generatePdf(vpToken);

            // Step 7: Call webhook
            vpProcessService.callWebhook(pdfs, result, vpRequest.getBankCredential().getBankWebhookUrl(),vpRequest.getBankCredential().getApiKey());

            // Step 8: Return final result
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (VpRequestNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDto(e.getErrorCode()));
        } catch (BankWebHookException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Bank webhook failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error occurred", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected server error");
        }
    }

}
