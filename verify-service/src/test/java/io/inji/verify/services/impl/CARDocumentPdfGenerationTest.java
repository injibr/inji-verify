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
    void shouldGeneratePdfFromSample() throws Exception {
        String html = generateHtmlFromSample("car-document-credential-sample.json");

        assertTrue(html.contains("Canutama"));
        assertTrue(html.contains("AM-1300904-0B225E133A9848CDAC3C9A1A5EA27673"));
        // Todos os campos de sobreposição são nulos, seção deve ser removida
        assertFalse(html.contains("BEGIN_SOBREPOSICOES"));

        writePdf(html, "MGI-CARDocument.pdf");
    }

    @Test
    void shouldGeneratePdfFromSampleSobreposicao() throws Exception {
        String html = generateHtmlFromSample("car-document-credential-sample-sobreposicao.json");

        assertTrue(html.contains("Canutama"));
        assertTrue(html.contains("AM-1300904-0B225E133A9848CDAC3C9A1A5EA27673"));
        assertTrue(html.contains("Embargo"));
        assertTrue(html.contains("Unidade de Conserva"));
        // Tabela deve estar presente com as 3 sobreposições
        assertTrue(html.contains("BEGIN_SOBREPOSICOES"));

        writePdf(html, "MGI-CARDocument-sobreposicao.pdf");
    }

    @Test
    void shouldGeneratePdfFromSample1Sobreposicao() throws Exception {
        String html = generateHtmlFromSample("car-document-credential-sample-1-sobreposicao.json");

        assertTrue(html.contains("Canutama"));
        assertTrue(html.contains("AM-1300904-0B225E133A9848CDAC3C9A1A5EA27673"));
        assertTrue(html.contains("Unidade de Conserva"));

        writePdf(html, "MGI-CARDocument-1-sobreposicao.pdf");
    }

    private String generateHtmlFromSample(String sampleFile) throws Exception {
        String vc = new String(
                getClass().getClassLoader().getResourceAsStream(sampleFile).readAllBytes()
        );

        Map<String, String> credentialMap = vcParserService.extractCredentialSubject(vc, 0);
        String issuerId = vcParserService.getValueFromVcMetadata(vc, "issuer", 0);
        String credentialType = vcParserService.getTypesInVerifiableCredential(vc, 0);

        assertEquals("MGI", issuerId);
        assertEquals("CARDocument", credentialType);

        return htmlGenerator.replaceAndGetHtml(credentialMap, issuerId, credentialType);
    }

    private void writePdf(String html, String fileName) throws Exception {
        ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
        ConverterProperties props = new ConverterProperties();
        props.setFontProvider(new DefaultFontProvider(true, false, false));
        HtmlConverter.convertToPdf(html, new PdfWriter(pdfOutput), props);

        assertTrue(pdfOutput.size() > 0);

        Path outputPath = Path.of(System.getProperty("user.dir"), fileName);
        Files.write(outputPath, pdfOutput.toByteArray());
        assertTrue(new File(outputPath.toString()).exists());
        System.out.println("PDF gerado: " + outputPath);
    }
}
