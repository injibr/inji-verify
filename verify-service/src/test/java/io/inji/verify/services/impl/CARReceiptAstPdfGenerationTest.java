package io.inji.verify.services.impl;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import io.inji.verify.services.VcParserService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CARReceiptAstPdfGenerationTest {

    private final VcParserService vcParserService = new VcParserServiceImpl();
    private final CARReceiptHtmlGeneratorServiceImpl htmlGenerator = new CARReceiptHtmlGeneratorServiceImpl();

    @Test
    void shouldParseAndGeneratePdfWithProprietarios() throws Exception {
        String vc = new String(
                getClass().getClassLoader().getResourceAsStream("car-receipt-ast-credential-sample.json").readAllBytes()
        );

        Map<String, String> credentialMap = vcParserService.extractCredentialSubject(vc, 0);
        String issuerId = vcParserService.getValueFromVcMetadata(vc, "issuer", 0);
        String credentialType = vcParserService.getTypesInVerifiableCredential(vc, 0);

        assertEquals("MGI", issuerId);
        assertEquals("CARReceipt", credentialType);
        assertTrue(credentialMap.containsKey("proprietarios"));
        assertTrue(credentialMap.get("proprietarios").contains("BEATRIZ COELHO DAS NEVES"));

        String html = htmlGenerator.replaceAndGetHtml(credentialMap, issuerId, "CARReceiptAST");

        assertTrue(html.contains("BEATRIZ COELHO DAS NEVES"));
        assertTrue(html.contains("JURANDY DA SILVA LOPES JUNIOR"));
        assertTrue(html.contains("607.808.593-08"));

        // First pass: generate PDF
        ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
        ConverterProperties props = new ConverterProperties();
        props.setFontProvider(new DefaultFontProvider(true, false, false));
        HtmlConverter.convertToPdf(html, new PdfWriter(pdfOutput), props);

        // Second pass: add footers
        byte[] qrBytes = htmlGenerator.getQrCodeBytes(credentialMap.get("codigoImovel"));
        java.io.ByteArrayInputStream pdfInput = new java.io.ByteArrayInputStream(pdfOutput.toByteArray());
        ByteArrayOutputStream finalOutput = new ByteArrayOutputStream();
        com.itextpdf.kernel.pdf.PdfReader pdfReader = new com.itextpdf.kernel.pdf.PdfReader(pdfInput);
        PdfDocument pdfDoc = new PdfDocument(pdfReader, new PdfWriter(finalOutput));
        new PageFooterEventHandler("CAR \u2013 Cadastro Ambiental Rural", qrBytes).writeFooters(pdfDoc);
        pdfDoc.close();

        assertTrue(finalOutput.size() > 0);

        Path outputPath = Path.of(System.getProperty("user.dir"), "MGI-CARReceiptAST.pdf");
        Files.write(outputPath, finalOutput.toByteArray());
        assertTrue(new File(outputPath.toString()).exists());
        System.out.println("PDF gerado: " + outputPath);
    }
}
