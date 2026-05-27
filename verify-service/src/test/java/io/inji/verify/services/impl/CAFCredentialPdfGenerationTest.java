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

class CAFCredentialPdfGenerationTest {

    private final VcParserService vcParserService = new VcParserServiceImpl();
    private final CAFCredentialHtmlGeneratorServiceImpl htmlGenerator = new CAFCredentialHtmlGeneratorServiceImpl();

    @Test
    void shouldParseAndGeneratePdfWithMembrosAndAreas() throws Exception {
        String vc = new String(
                getClass().getClassLoader().getResourceAsStream("caf-credential-sample.json").readAllBytes()
        );

        Map<String, String> credentialMap = vcParserService.extractCredentialSubject(vc, 0);
        String issuerId = vcParserService.getValueFromVcMetadata(vc, "issuer", 0);
        String credentialType = vcParserService.getTypesInVerifiableCredential(vc, 0);

        assertEquals("MDA", issuerId);
        assertEquals("CAFCredential", credentialType);
        assertTrue(credentialMap.containsKey("membros"));
        assertTrue(credentialMap.containsKey("areas"));
        assertTrue(credentialMap.get("membros").contains("Jose da Silva"));

        String html = htmlGenerator.replaceAndGetHtml(credentialMap, issuerId, credentialType);

        assertFalse(html.contains("REPLACEME-->membros"));
        assertTrue(html.contains("Jose da Silva"));
        assertTrue(html.contains("Ana da Silva"));
        assertTrue(html.contains("11122233344"));

        ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
        ConverterProperties props = new ConverterProperties();
        props.setFontProvider(new DefaultFontProvider(true, false, false));
        HtmlConverter.convertToPdf(html, new PdfWriter(pdfOutput), props);

        assertTrue(pdfOutput.size() > 0);

        Path outputPath = Path.of(System.getProperty("user.dir"), "MDA-CAFCredential.pdf");
        Files.write(outputPath, pdfOutput.toByteArray());
        assertTrue(new File(outputPath.toString()).exists());
        System.out.println("PDF gerado: " + outputPath);
    }
}
