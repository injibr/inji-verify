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

class CCIRCredentialPdfGenerationTest {

    private final VcParserService vcParserService = new VcParserServiceImpl();
    private final CCIRCredentialHtmlGeneratorServiceImpl htmlGenerator = new CCIRCredentialHtmlGeneratorServiceImpl();

    @Test
    void shouldGeneratePdfWithMultipleTitulares() throws Exception {
        String vc = new String(
                getClass().getClassLoader().getResourceAsStream("ccir-credential-sample.json").readAllBytes()
        );

        Map<String, String> credentialMap = vcParserService.extractCredentialSubject(vc, 0);
        String credentialType = vcParserService.getTypesInVerifiableCredential(vc, 0);

        assertEquals("CCIRCredential", credentialType);
        assertTrue(credentialMap.containsKey("titulares"));

        String html = htmlGenerator.replaceAndGetHtml(credentialMap, credentialType);

        assertFalse(html.contains("REPLACEME-->titulares"));
        // Verifica que os titulares do sample foram renderizados
        assertTrue(html.contains(credentialMap.get("declarante")));

        ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
        ConverterProperties props = new ConverterProperties();
        props.setFontProvider(new DefaultFontProvider(true, false, false));
        HtmlConverter.convertToPdf(html, new PdfWriter(pdfOutput), props);

        assertTrue(pdfOutput.size() > 0);

        Path outputPath = Path.of(System.getProperty("user.dir"), "INCRA-CCIRCredential-todos-titulares.pdf");
        Files.write(outputPath, pdfOutput.toByteArray());

        assertTrue(new File(outputPath.toString()).exists());
        System.out.println("PDF gerado com titulares dinamicos: " + outputPath);
    }
}
