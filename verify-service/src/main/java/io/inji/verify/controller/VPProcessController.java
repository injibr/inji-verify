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
import io.inji.verify.models.VpRequest;
import io.inji.verify.services.*;
import io.inji.verify.shared.Constants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final VpRequestService vpRequestService;

    public VPProcessController(VerifiablePresentationRequestService verifiablePresentationRequestService, VerifiablePresentationSubmissionService verifiablePresentationSubmissionService, Gson gson, BankWebhookService bankWebhookService, PdfService pdfService, VpRequestService vpRequestService) {
        this.verifiablePresentationRequestService = verifiablePresentationRequestService;
        this.verifiablePresentationSubmissionService = verifiablePresentationSubmissionService;
        this.gson = gson;
        this.bankWebhookService = bankWebhookService;
        this.pdfService = pdfService;
        this.vpRequestService = vpRequestService;
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
    public ResponseEntity<?> submitVP(@NotNull @NotBlank @RequestParam(value = "vp_token") String vpToken, @NotNull @NotBlank @RequestParam(value = "presentation_submission") String presentationSubmission, @NotNull @NotBlank @RequestParam(value = "state") String state) {
        //direct-post
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
        VpRequest vpRequestsByRequestId = vpRequestService.getVpRequestsByRequestId(state);
        if (Objects.isNull(vpRequestsByRequestId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDto(ErrorCode.NO_VP_REQUEST));
        }
        List<String> requestIds = verifiablePresentationRequestService.getLatestRequestIdFor(vpRequestsByRequestId.getTransactionId());
        VPTokenResultDto result;
        if (!requestIds.isEmpty()) {
            try {
                result = verifiablePresentationSubmissionService.getVPResult(requestIds, vpRequestsByRequestId.getTransactionId());
                if (result.getVpResultStatus() == VPResultStatus.FAILED) {
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
        Map<String, ByteArrayInputStream> pdfBytes = pdfService.generatePdf(vpToken);

        //Calling the webhook
        try {
            bankWebhookService.callWebhook(pdfBytes, result, vpRequestsByRequestId.getBankCredential().getBankWebhookUrl());
        } catch (BankWebHookException be) {
            log.error(be.getMessage());
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        //Sending pdf as result
        return ResponseEntity.status(HttpStatus.OK).body(result);

    }

}
