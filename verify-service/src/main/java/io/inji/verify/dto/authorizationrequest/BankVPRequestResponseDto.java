package io.inji.verify.dto.authorizationrequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * INJIBR-CUSTOM: resposta do VP request para o fluxo do webhook do banco.
 * Idêntica ao VPRequestResponseDto, mas usa BankAuthorizationRequestResponseDto
 * para substituir o responseUri pelo endpoint /vp-process.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BankVPRequestResponseDto {

    private final String transactionId;
    private final String requestId;
    private final BankAuthorizationRequestResponseDto authorizationDetails;
    private final Long expiresAt;
    private final String requestUri;

    public BankVPRequestResponseDto(VPRequestResponseDto original, String bankResponseUri) {
        this.transactionId = original.getTransactionId();
        this.requestId = original.getRequestId();
        this.authorizationDetails = original.getAuthorizationDetails() != null
                ? new BankAuthorizationRequestResponseDto(original.getAuthorizationDetails(), bankResponseUri)
                : null;
        this.expiresAt = original.getExpiresAt();
        this.requestUri = original.getRequestUri();
    }
}
