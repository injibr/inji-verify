package io.inji.verify.exception;

import io.inji.verify.enums.ErrorCode;
/**
 * Exception thrown when PDF generation fails.
 */
public class PdfGenerationException extends RuntimeException {
    private static final String message = ErrorCode.PDF_GENERATION_FAILED.getErrorMessage();

    public PdfGenerationException() {
        super(message);
    }
}
