package io.inji.verify.exception;

import io.inji.verify.enums.ErrorCode;
/**
 * Exception thrown when there is an error parsing a PDF document.
 */
public class PdfParseException extends RuntimeException {
    private static final String message = ErrorCode.PDF_PARSE_FAILED.getErrorMessage();

    public PdfParseException() {
        super(message);
    }
}
