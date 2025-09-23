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
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    @Value("${mosip.openid.htmlTemplate}")
    private String credentialTemplatePath;

    private final VcParserService vcParserService;

    private String credentialTemplateHtmlString = null;


    /**
     * Constructor for PdfServiceImpl.
     *
     * @param vcParserService the service used to parse verifiable credentials
     */
    public PdfServiceImpl(VcParserService vcParserService) {
        this.vcParserService = vcParserService;
    }


    @PostConstruct
    public void setUp() throws IOException {
            Resource credentialTemplateResource = new ClassPathResource("templates/"+ credentialTemplatePath);
            credentialTemplateHtmlString = (Files.readString(credentialTemplateResource.getFile().toPath()));
    }

    /**
     * Retrieves the HTML template string for a given issuer ID and credential type.
     * If a specific template is not found, it falls back to a default template.
     *
     * @param issuerId       the ID of the issuer
     * @param credentialType the type of the credential
     * @return the HTML template string
     */
    private String getCredentialSupportedTemplateString(String issuerId, String credentialType) {
        String templateFileName = String.format("%s-%s-template.html", issuerId, credentialType);
            Path basePath = Paths.get("src/main/resources/templates").toAbsolutePath().normalize();
            Path resolvedPath = basePath.resolve(templateFileName).normalize();

            if (!resolvedPath.startsWith(basePath)) {
                throw new SecurityException("Attempted path traversal attack: " + resolvedPath);
            }
            try {
                return new String(
                        Objects.requireNonNull(PdfService.class.getClassLoader()
                                        .getResourceAsStream("templates/"+templateFileName))
                                .readAllBytes()
                );
            } catch (IOException e) {
                log.error("Error while reading specific template file, falling back to default template", e);
            }
            return credentialTemplateHtmlString;
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
            String mergedHtml = getCredentialSupportedTemplateString(issuerId,credentialType); // start with the original template

            for (String key : data.keySet()) {
                try{
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, data.get(key));
                } catch(IllegalArgumentException ex){
                    log.error("Error while replacing key in template {}",key);
                    // If there's an error (e.g., special characters in the value), remove the placeholder
                  mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, "");
                }
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            PdfWriter pdfwriter = new PdfWriter(outputStream);
            DefaultFontProvider defaultFont = new DefaultFontProvider(true, false, false);
            ConverterProperties converterProperties = new ConverterProperties();
            converterProperties.setFontProvider(defaultFont);
            HtmlConverter.convertToPdf(mergedHtml, pdfwriter, converterProperties);
            return new ByteArrayInputStream(outputStream.toByteArray());
        }catch (Exception ex){
            throw new PdfParseException();
        }
    }

    /**     * Generates a PDF from the provided verifiable credential (VC).
     *
     * @param vc the verifiable credential in JSON format
     * @return a List<ByteArrayInputStream> containing the generated PDF
     * @throws PdfParseException      if there is an error parsing the VC
     * @throws PdfGenerationException if there is an error generating the PDF
     */
    @Override
    public  Map<String,ByteArrayInputStream> generatePdf(String vc) {
        Map<String, String> credentialMap;
        String issuerId;
        String credentialType;
        int totalVCs;
        Map<String,ByteArrayInputStream> pdfStreams = new HashMap<>();
        try {
            totalVCs= vcParserService.getTotalNumberOfVc(vc);
        }catch (JsonProcessingException ex){
            log.error("Error while parsing vc",ex);
            throw new PdfParseException();
        }
        for (int i = 0; i < totalVCs; i++) {
            try {
                credentialMap = vcParserService.extractCredentialSubject(vc, i);
                issuerId = vcParserService.getValueFromVcMetadata(vc,"issuer", i);
                credentialType = vcParserService.getValueFromVcMetadata(vc,"credentialType", i);
            }catch (JsonProcessingException ex){
                log.error("Error while parsing vc",ex);
                throw new PdfParseException();
            }
            if(!Objects.isNull(credentialMap) && !Objects.isNull(issuerId) && !Objects.isNull(credentialType)){
                pdfStreams.put(credentialType,renderPdf(credentialMap, issuerId, credentialType));
            }else {
                log.error("Error while generating pdf");
                throw new PdfGenerationException();
            }
        }
       return pdfStreams;
    }
}
