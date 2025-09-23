package io.inji.verify.exception;

import io.inji.verify.enums.ErrorCode;
import lombok.Getter;

@Getter
public class VpRequestNotFoundException extends RuntimeException {
    private final ErrorCode errorCode;

    public VpRequestNotFoundException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}