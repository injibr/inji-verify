package io.inji.verify.exception;

import io.inji.verify.enums.ErrorCode;
import lombok.Getter;

// INJIBR-CUSTOM: extended VPRequestNotFoundException to support ErrorCode for bank webhook flow
@Getter
public class VPRequestNotFoundException extends RuntimeException {
    private static final String defaultMessage = ErrorCode.NO_AUTH_REQUEST.getErrorMessage();
    private ErrorCode errorCode;

    public VPRequestNotFoundException() {
        super(defaultMessage);
    }

    public VPRequestNotFoundException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}