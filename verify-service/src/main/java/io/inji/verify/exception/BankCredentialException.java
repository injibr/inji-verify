package io.inji.verify.exception;
import io.inji.verify.enums.ErrorCode;

/**
 * Custom exception for handling bank webhook errors.
 */
public class BankCredentialException extends RuntimeException {
    private static final String message = ErrorCode.BANK_CREDENTIAL_ERROR.getErrorMessage();

    public BankCredentialException() {
        super(message);
    }
}
