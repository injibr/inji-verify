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

class CARDocumentPdfGenerationTest {

    private final VcParserService vcParserService = new VcParserServiceImpl();
    private final CARDocumentHtmlGeneratorServiceImpl htmlGenerator = new CARDocumentHtmlGeneratorServiceImpl();

    @Test
    void shouldParseAndGeneratePdfWithSobreposicoes() throws Exception {
        String vc = new String(
                getClass().getClassLoader().getResourceAsStream("car-document-credential-sample.json").readAllBytes()
        );

        Map<String, String> credentialMap = vcParserService.extractCredentialSubject(vc, 0);
        String issuerId = vcParserService.getValueFromVcMetadata(vc, "issuer", 0);
        String credentialType = vcParserService.getTypesInVerifiableCredential(vc, 0);

        assertEquals("MGI", issuerId);
        assertEquals("CARDocument", credentialType);
        assertTrue(credentialMap.containsKey("sobreposicoesAreasEmbargadas"));
        assertTrue(credentialMap.containsKey("sobreposicoesUnidadeConservacao"));
        assertTrue(credentialMap.containsKey("sobreposicoesTerraIndigena"));

        String html = htmlGenerator.replaceAndGetHtml(credentialMap, issuerId, credentialType);

        assertTrue(html.contains("Trizidela do Vale"));
        assertTrue(html.contains("MA-2112233-044B8D8BC23345BE88179D0385A8BB1A"));
        assertTrue(html.contains("Embargo"));
        assertTrue(html.contains("Parque Nacional XYZ"));
        assertTrue(html.contains("Terra Indigena ABC"));

        ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
        ConverterProperties props = new ConverterProperties();
        props.setFontProvider(new DefaultFontProvider(true, false, false));
        HtmlConverter.convertToPdf(html, new PdfWriter(pdfOutput), props);

        assertTrue(pdfOutput.size() > 0);

        Path outputPath = Path.of(System.getProperty("user.dir"), "MGI-CARDocument.pdf");
        Files.write(outputPath, pdfOutput.toByteArray());
        assertTrue(new File(outputPath.toString()).exists());
        System.out.println("PDF gerado: " + outputPath);
    }
}
