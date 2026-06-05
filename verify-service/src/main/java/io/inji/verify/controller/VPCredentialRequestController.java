package io.inji.verify.controller;

import io.inji.verify.dto.authorizationrequest.BankVPRequestResponseDto;
import io.inji.verify.dto.authorizationrequest.VPRequestCreateDto;
import io.inji.verify.dto.authorizationrequest.VPRequestResponseDto;
import io.inji.verify.dto.core.ErrorDto;
import io.inji.verify.enums.ErrorCode;
import io.inji.verify.exception.BankCredentialException;
import io.inji.verify.exception.PresentationDefinitionNotFoundException;
import io.inji.verify.services.VerifiablePresentationRequestService;
import io.inji.verify.services.VpRequestService;
import io.inji.verify.shared.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * INJIBR-CUSTOM: controller para o fluxo de requisição de credenciais do banco.
 * Cria um VP request e substitui o responseUri pelo endpoint do webhook do banco (/vp-process).
 */
@RequestMapping("/vp-credential-request")
@RestController
@Validated
@Slf4j
public class VPCredentialRequestController {
    final VerifiablePresentationRequestService verifiablePresentationRequestService;
    private final VpRequestService vpRequestService;

    @Value("${inji.vp-submission.base-url}")
    String verifyServiceBaseUrl;

    public VPCredentialRequestController(VerifiablePresentationRequestService verifiablePresentationRequestService, VpRequestService vpRequestService) {
        this.verifiablePresentationRequestService = verifiablePresentationRequestService;
        this.vpRequestService = vpRequestService;
    }


    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handleVpRequest(
            @RequestHeader("x-bank-id") String bankId,
            @RequestHeader("x-bank-secret") String bankSecret,
            @RequestBody VPRequestCreateDto vpRequestCreate) {
        if (vpRequestCreate.getPresentationDefinitionId() == null && vpRequestCreate.getPresentationDefinition() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorDto(ErrorCode.BOTH_ID_AND_PD_CANNOT_BE_NULL));
        }
        try {
            VPRequestResponseDto authorizationRequestResponse = verifiablePresentationRequestService.createAuthorizationRequest(vpRequestCreate);
            String bankResponseUri = verifyServiceBaseUrl + Constants.RESPONSE_SUBMISSION_URI_ROOT + Constants.RESPONSE_PROCESS_URI_ROOT;
            BankVPRequestResponseDto bankResponse = new BankVPRequestResponseDto(authorizationRequestResponse, bankResponseUri);
            vpRequestService.saveVpRequest(bankId, bankSecret, authorizationRequestResponse.getRequestId(), authorizationRequestResponse.getTransactionId());
            return ResponseEntity.status(HttpStatus.CREATED).body(bankResponse);
        } catch (PresentationDefinitionNotFoundException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDto(ErrorCode.NO_PRESENTATION_DEFINITION));
        } catch (BankCredentialException e){
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorDto(ErrorCode.BANK_CREDENTIAL_ERROR));
        }
    }

}
