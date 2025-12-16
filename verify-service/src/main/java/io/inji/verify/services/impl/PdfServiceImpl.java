package io.inji.verify.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.kernel.pdf.PdfWriter;
import io.inji.verify.exception.PdfGenerationException;
import io.inji.verify.exception.PdfParseException;
import io.inji.verify.services.PdfService;
import io.inji.verify.services.VcParserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Service implementation for generating PDFs from Verifiable Credentials (VCs).
 * <p>
 * This service uses Apache Velocity for templating and iText for PDF generation.
 * It supports different templates based on the issuer ID and credential type.
 */
@Slf4j
@Service
public class PdfServiceImpl implements PdfService {

    private final VcParserService vcParserService;

    private final HtmlGeneratorFactory htmlGeneratorFactory;

    /**
     * Constructor for PdfServiceImpl.
     *
     * @param vcParserService the service used to parse verifiable credentials
     */
    public PdfServiceImpl(VcParserService vcParserService, HtmlGeneratorFactory htmlGeneratorFactory) {
        this.vcParserService = vcParserService;
        this.htmlGeneratorFactory = htmlGeneratorFactory;
    }
    /**
     * Renders a PDF from the provided data using the specified issuer ID and credential type.
     *
     * @param data           the data to be included in the PDF
     * @param issuerId       the ID of the issuer
     * @param credentialType the type of the credential
     * @return a ByteArrayInputStream containing the generated PDF
     */
    private ByteArrayInputStream renderPdf(Map<String, String> data, String issuerId, String credentialType) {
        try {
            String htmlGeneratorType;
            if (!Objects.isNull(data.get("tipoImovel")) && data.get("tipoImovel").equals("AST")){
                credentialType = "CARReceiptAST";
                htmlGeneratorType = "CarReceiptAstHtmlGeneratorServiceImpl";
            } else if (credentialType.equals("CAFCredential")) {
                htmlGeneratorType = "CAFCredentialHtmlGeneratorServiceImpl";
            }else if (credentialType.equals("CARDocument")) {
                htmlGeneratorType = "CARDocumentHtmlGeneratorServiceImpl";
            }  else if (!Objects.isNull(data.get("tipoImovel")) && data.get("tipoImovel").equals("PCT")){
                htmlGeneratorType = "defaultHtmlGeneratorService";
                credentialType = "CARReceiptPCT";
            }else {
                htmlGeneratorType = "defaultHtmlGeneratorService";
            }

            String html = htmlGeneratorFactory.getHtmlGeneratorService(htmlGeneratorType)
                    .replaceAndGetHtml(data, issuerId, credentialType);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            PdfWriter pdfwriter = new PdfWriter(outputStream);
            DefaultFontProvider defaultFont = new DefaultFontProvider(true, false, false);
            ConverterProperties converterProperties = new ConverterProperties();
            converterProperties.setFontProvider(defaultFont);
            HtmlConverter.convertToPdf(html, pdfwriter, converterProperties);
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (Exception ex) {
            throw new PdfParseException();
        }
    }


    /**
     * Generates a PDF from the provided verifiable credential (VC).
     *
     * @param vc the verifiable credential in JSON format
     * @return a List<ByteArrayInputStream> containing the generated PDF
     * @throws PdfParseException      if there is an error parsing the VC
     * @throws PdfGenerationException if there is an error generating the PDF
     */
    @Override
    public Map<String, ByteArrayInputStream> generatePdf(String vc) {
        Map<String, String> credentialMap;
        String issuerId;
        String credentialType;
        int totalVCs;
        Map<String, ByteArrayInputStream> pdfStreams = new HashMap<>();
        try {
            totalVCs = vcParserService.getTotalNumberOfVc(vc);
        } catch (JsonProcessingException ex) {
            log.error("Error while parsing vc", ex);
            throw new PdfParseException();
        }
        for (int i = 0; i < totalVCs; i++) {
            try {
                credentialMap = vcParserService.extractCredentialSubject(vc, i);
                issuerId = vcParserService.getValueFromVcMetadata(vc, "issuer", i);
                credentialType = vcParserService.getTypesInVerifiableCredential(vc, i);
            } catch (JsonProcessingException ex) {
                log.error("Error while parsing vc", ex);
                throw new PdfParseException();
            }
            if (!Objects.isNull(credentialMap) && !Objects.isNull(issuerId) && !Objects.isNull(credentialType)) {
                pdfStreams.put(credentialType, renderPdf(credentialMap, issuerId, credentialType));
            } else {
                log.error("Error while generating pdf");
                throw new PdfGenerationException();
            }
        }
        return pdfStreams;
    }
}
