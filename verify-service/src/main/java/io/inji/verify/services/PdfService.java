package io.inji.verify.services;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

/** Service to generate PDF from a given Verifiable Credential (VC) */
public interface PdfService {
    Map<String,ByteArrayInputStream> generatePdf(String vc);
}
