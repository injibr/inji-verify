package io.inji.verify.controller;

import com.nimbusds.jose.shaded.gson.Gson;
import io.inji.verify.dto.authorizationrequest.VPRequestStatusDto;
import io.inji.verify.dto.core.ErrorDto;
import io.inji.verify.dto.submission.PresentationSubmissionDto;
import io.inji.verify.dto.submission.VPSubmissionDto;
import io.inji.verify.dto.submission.VPTokenResultDto;
import io.inji.verify.enums.ErrorCode;
import io.inji.verify.enums.VPResultStatus;
import io.inji.verify.exception.BankWebHookException;
import io.inji.verify.exception.VPSubmissionNotFoundException;
import io.inji.verify.services.*;
import io.inji.verify.shared.Constants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;

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
    private final VerifiablePresentationRequestService verifiablePresentationRequestService;

    private final VerifiablePresentationSubmissionService verifiablePresentationSubmissionService;

    private final Gson gson;

    private final BankWebhookService bankWebhookService;

    private final PdfService pdfService;

    public VPProcessController(VerifiablePresentationRequestService verifiablePresentationRequestService, VerifiablePresentationSubmissionService verifiablePresentationSubmissionService, Gson gson, BankWebhookService bankWebhookService, PdfService pdfService) {
        this.verifiablePresentationRequestService = verifiablePresentationRequestService;
        this.verifiablePresentationSubmissionService = verifiablePresentationSubmissionService;
        this.gson = gson;
        this.bankWebhookService = bankWebhookService;
        this.pdfService = pdfService;
    }

    /**
     * Handles the submission of a Verifiable Presentation (VP) via the `/vp-process` endpoint.
     * <p>
     * Accepts parameters in x-www-form-urlencoded format, validates the presentation submission,
     * processes the VP, triggers a webhook, and returns a generated PDF as a response.
     *
     * @param vpToken                 the VP token as a String (required)
     * @param presentationSubmission  the presentation submission as a JSON String (required)
     * @param state                   the state parameter to correlate the request (required)
     * @param transactionId           the transaction ID associated with the request (optional)
     * @return                        a ResponseEntity containing the generated PDF or an error response
     */
    @PostMapping(path = "/vp-process", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> submitVP(@NotNull @NotBlank @RequestParam(value = "vp_token") String vpToken, @NotNull @NotBlank @RequestParam(value = "presentation_submission") String presentationSubmission, @NotNull @NotBlank @RequestParam(value = "state") String state, String transactionId) {
        PresentationSubmissionDto presentationSubmissionDto = gson.fromJson(presentationSubmission, PresentationSubmissionDto.class);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<PresentationSubmissionDto>> violations = validator.validate(presentationSubmissionDto);
        if (!violations.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(violations.iterator().next().getMessage());
        }
        VPSubmissionDto vpSubmissionDto = new VPSubmissionDto(vpToken, presentationSubmissionDto, state);

        VPRequestStatusDto currentVPRequestStatusDto = verifiablePresentationRequestService.getCurrentRequestStatus(vpSubmissionDto.getState());
        if (currentVPRequestStatusDto == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        verifiablePresentationSubmissionService.submit(vpSubmissionDto);


//vp-result
        List<String> requestIds = verifiablePresentationRequestService.getLatestRequestIdFor(transactionId);
        if (!requestIds.isEmpty()) {
            try {
                VPTokenResultDto result = verifiablePresentationSubmissionService.getVPResult(requestIds, transactionId);
                if(result.getVpResultStatus() == VPResultStatus.FAILED){
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDto(ErrorCode.NO_VP_SUBMISSION));
                }
            } catch (VPSubmissionNotFoundException e) {
                log.error(e.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDto(ErrorCode.NO_VP_SUBMISSION));
            }
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDto(ErrorCode.INVALID_TRANSACTION_ID));
        }

        //Preparing pdf
        ByteArrayInputStream pdfBytes = pdfService.generatePdf(vpToken);

        //Calling the webhook
        try {
            bankWebhookService.callWebhook();
        }catch (BankWebHookException be){
            log.error(be.getMessage());
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        //Sending pdf as result
        return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition")
                .body(new InputStreamResource(pdfBytes));
    }

}
