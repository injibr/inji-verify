package io.inji.verify.exception;
import io.inji.verify.enums.ErrorCode;

/**
 * Custom exception for handling bank webhook errors.
 */
public class BankWebHookException extends RuntimeException {
    private static final String message = ErrorCode.BANK_WEBHOOK_ERROR.getErrorMessage();

    public BankWebHookException() {
        super(message);
    }
}
