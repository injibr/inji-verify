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

class CARReceiptPctPdfGenerationTest {

    private final VcParserService vcParserService = new VcParserServiceImpl();
    private final CARReceiptHtmlGeneratorServiceImpl htmlGenerator = new CARReceiptHtmlGeneratorServiceImpl();

    @Test
    void shouldParseAndGeneratePdfForCARReceiptPCT() throws Exception {
        String vc = new String(
                getClass().getClassLoader().getResourceAsStream("car-receipt-pct-credential-sample.json").readAllBytes()
        );

        Map<String, String> credentialMap = vcParserService.extractCredentialSubject(vc, 0);
        String issuerId = vcParserService.getValueFromVcMetadata(vc, "issuer", 0);
        String credentialType = vcParserService.getTypesInVerifiableCredential(vc, 0);

        assertEquals("MGI", issuerId);
        assertEquals("CARReceipt", credentialType);
        assertTrue(credentialMap.containsKey("proprietarios"));

        String html = htmlGenerator.replaceAndGetHtml(credentialMap, issuerId, "CARReceiptPCT");

        assertTrue(html.contains("TERRITÓRIO QUILOMBOLA PALMARES"));
        assertTrue(html.contains("ASSOCIACAO QUILOMBOLA PALMARES"));
        assertTrue(html.contains("Salvador"));

        ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
        ConverterProperties props = new ConverterProperties();
        props.setFontProvider(new DefaultFontProvider(true, false, false));
        HtmlConverter.convertToPdf(html, new PdfWriter(pdfOutput), props);

        assertTrue(pdfOutput.size() > 0);

        Path outputPath = Path.of(System.getProperty("user.dir"), "MGI-CARReceiptPCT.pdf");
        Files.write(outputPath, pdfOutput.toByteArray());
        assertTrue(new File(outputPath.toString()).exists());
        System.out.println("PDF gerado: " + outputPath);
    }
}
