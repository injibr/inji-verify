package io.inji.verify.services;

import java.io.ByteArrayInputStream;
/** Service to generate PDF from a given Verifiable Credential (VC) */
public interface PdfService {
    ByteArrayInputStream generatePdf(String vc);
}
