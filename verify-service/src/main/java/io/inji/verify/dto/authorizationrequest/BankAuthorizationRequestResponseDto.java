package io.inji.verify.dto.authorizationrequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.inji.verify.dto.presentation.VPDefinitionResponseDto;
import lombok.Getter;

/**
 * INJIBR-CUSTOM: wrapper sobre AuthorizationRequestResponseDto que substitui o responseUri
 * pelo endpoint do webhook do banco (/vp-process), sem alterar o DTO upstream.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BankAuthorizationRequestResponseDto {

    private final String responseType;
    private final String responseMode;
    private final long issuedAt;
    private final String clientId;
    private final String presentationDefinitionUri;
    private final VPDefinitionResponseDto presentationDefinition;
    private final String nonce;
    private final String responseUri;
    private final boolean acceptVPWithoutHolderProof;

    public BankAuthorizationRequestResponseDto(AuthorizationRequestResponseDto original, String bankResponseUri) {
        this.responseType = original.getResponseType();
        this.responseMode = original.getResponseMode();
        this.issuedAt = original.getIssuedAt();
        this.clientId = original.getClientId();
        this.presentationDefinitionUri = original.getPresentationDefinitionUri();
        this.presentationDefinition = original.getPresentationDefinition();
        this.nonce = original.getNonce();
        this.responseUri = bankResponseUri;
        this.acceptVPWithoutHolderProof = original.isAcceptVPWithoutHolderProof();
    }
}
