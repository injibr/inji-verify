package io.inji.verify.services.impl;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.io.image.ImageDataFactory;

public class PageFooterEventHandler {

    private final String footerText;
    private final byte[] qrCodeBytes;

    public PageFooterEventHandler(String footerText, byte[] qrCodeBytes) {
        this.footerText = footerText;
        this.qrCodeBytes = qrCodeBytes;
    }

    public void writeFooters(PdfDocument pdfDoc) {
        int totalPages = pdfDoc.getNumberOfPages();
        for (int i = 1; i <= totalPages; i++) {
            PdfPage page = pdfDoc.getPage(i);
            Rectangle pageSize = page.getPageSize();
            float x = pageSize.getLeft() + 18;
            float y = pageSize.getBottom() + 10;
            float width = pageSize.getWidth() - 36;

            PdfCanvas pdfCanvas = new PdfCanvas(page);

            // Draw line
            pdfCanvas.moveTo(x, y + 55);
            pdfCanvas.lineTo(x + width, y + 55);
            pdfCanvas.stroke();

            // Draw footer text
            try {
                PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                try (Canvas canvas = new Canvas(pdfCanvas, new Rectangle(x, y, width, 55))) {
                    canvas.add(new Paragraph(footerText)
                            .setFont(font)
                            .setFontSize(11)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginBottom(2));
                    canvas.add(new Paragraph("Página " + i + "/" + totalPages)
                            .setFont(font)
                            .setFontSize(9)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginTop(0));
                }
            } catch (Exception ignored) {}

            // Draw QR code
            if (qrCodeBytes != null) {
                try {
                    Image qrImage = new Image(ImageDataFactory.create(qrCodeBytes));
                    qrImage.setFixedPosition(pageSize.getRight() - 78, y);
                    qrImage.setWidth(50);
                    qrImage.setHeight(50);
                    try (Canvas canvas = new Canvas(pdfCanvas, pageSize)) {
                        canvas.add(qrImage);
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
