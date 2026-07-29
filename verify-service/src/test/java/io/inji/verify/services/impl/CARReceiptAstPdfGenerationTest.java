package io.inji.verify.services.impl;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.kernel.pdf.PdfWriter;
import io.inji.verify.services.VcParserService;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CARReceiptAstPdfGenerationTest {
    private final VcParserService vcParserService = new VcParserServiceImpl();
    private final CARReceiptHtmlGeneratorServiceImpl htmlGenerator = new CARReceiptHtmlGeneratorServiceImpl();

    @Test
    void comMatricula() throws Exception {
        Map<String, String> cs = buildCs("car-receipt-ast-credential-sample.json");
        String html = htmlGenerator.replaceAndGetHtml(cs, "CARReceiptAST");
        assertTrue(html.contains("BEATRIZ COELHO DAS NEVES"));
        assertTrue(html.contains("BEGIN_MATRICULA"));
        pdf(html, "MGI-CARReceiptAST.pdf", cs);
    }

    @Test
    void semMatricula() throws Exception {
        Map<String, String> cs = buildCs("car-receipt-ast-credential-sample-sem-matricula.json");
        String html = htmlGenerator.replaceAndGetHtml(cs, "CARReceiptAST");
        assertFalse(html.contains("BEGIN_MATRICULA"));
        pdf(html, "MGI-CARReceiptAST-sem-matricula.pdf", cs);
    }

    @Test
    void muitosProprietarios() throws Exception {
        Map<String, String> cs = buildCs("car-receipt-ast-credential-sample-muitos-proprietarios.json");
        String html = htmlGenerator.replaceAndGetHtml(cs, "CARReceiptAST");
        assertTrue(html.contains("BEATRIZ COELHO DAS NEVES"));
        assertTrue(html.contains("ADRIANA CARVALHO DIAS"));
        pdf(html, "MGI-CARReceiptAST-muitos-proprietarios.pdf", cs);
    }

    private Map<String, String> buildCs(String file) throws Exception {
        String vc = new String(getClass().getClassLoader().getResourceAsStream(file).readAllBytes());
        return vcParserService.extractCredentialSubject(vc, 0);
    }

    private void pdf(String html, String name, Map<String, String> cs) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConverterProperties p = new ConverterProperties();
        p.setFontProvider(new DefaultFontProvider(true, false, false));
        HtmlConverter.convertToPdf(html, new PdfWriter(out), p);

        byte[] qrBytes = htmlGenerator.getQrCodeBytes(cs.get("codigoImovel"));
        java.io.ByteArrayInputStream pdfIn = new java.io.ByteArrayInputStream(out.toByteArray());
        ByteArrayOutputStream finalOut = new ByteArrayOutputStream();
        com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(
                new com.itextpdf.kernel.pdf.PdfReader(pdfIn), new PdfWriter(finalOut));
        new PageFooterEventHandler("CAR \u2013 Cadastro Ambiental Rural", qrBytes).writeFooters(pdfDoc);
        pdfDoc.close();

        Path path = Path.of(System.getProperty("user.dir"), name);
        Files.write(path, finalOut.toByteArray());
        System.out.println("PDF gerado: " + path);
    }
}
