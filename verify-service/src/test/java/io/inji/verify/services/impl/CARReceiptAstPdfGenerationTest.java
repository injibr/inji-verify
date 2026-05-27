package io.inji.verify.services.impl;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
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
    private final CarReceiptAstHtmlGeneratorServiceImpl htmlGenerator = new CarReceiptAstHtmlGeneratorServiceImpl();

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
        assertTrue(credentialMap.get("proprietarios").contains("Joao da Silva"));

        String html = htmlGenerator.replaceAndGetHtml(credentialMap, issuerId, "CARReceiptAST");

        assertFalse(html.contains("REPLACEME-->proprietarios"));
        assertTrue(html.contains("Joao da Silva"));
        assertTrue(html.contains("Maria Oliveira"));
        assertTrue(html.contains("12345678901"));

        ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
        ConverterProperties props = new ConverterProperties();
        props.setFontProvider(new DefaultFontProvider(true, false, false));
        HtmlConverter.convertToPdf(html, new PdfWriter(pdfOutput), props);

        assertTrue(pdfOutput.size() > 0);

        Path outputPath = Path.of(System.getProperty("user.dir"), "MGI-CARReceiptAST.pdf");
        Files.write(outputPath, pdfOutput.toByteArray());
        assertTrue(new File(outputPath.toString()).exists());
        System.out.println("PDF gerado: " + outputPath);
    }
}
