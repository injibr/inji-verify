package io.inji.verify.services.impl;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.kernel.pdf.PdfWriter;
import io.inji.verify.services.VcParserService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
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

        assertFalse(html.contains("REPLACEME-->sobreposicoesAreasEmbargadas"));
        assertFalse(html.contains("REPLACEME-->sobreposicoesUnidadeConservacao"));
        assertFalse(html.contains("REPLACEME-->sobreposicoesTerraIndigena"));
        assertTrue(html.contains("Embargo"));
        assertTrue(html.contains("Parque Nacional XYZ"));
        assertTrue(html.contains("Terra Indigena ABC"));

        ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();
        ConverterProperties props = new ConverterProperties();
        props.setFontProvider(new DefaultFontProvider(true, false, false));
        HtmlConverter.convertToPdf(html, new PdfWriter(pdfOutput), props);

        assertTrue(pdfOutput.size() > 0);
    }
}
