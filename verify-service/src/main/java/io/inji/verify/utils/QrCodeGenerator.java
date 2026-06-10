package io.inji.verify.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

public class QrCodeGenerator {

    private QrCodeGenerator() {}

    public static byte[] generatePng(String text, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.MARGIN, 0));
            // Crop white border
            int[] enclosingRect = matrix.getEnclosingRectangle();
            int x = enclosingRect[0];
            int y = enclosingRect[1];
            int w = enclosingRect[2];
            int h = enclosingRect[3];
            BitMatrix cropped = new BitMatrix(w, h);
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    if (matrix.get(col + x, row + y)) {
                        cropped.set(col, row);
                    }
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(cropped, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static String generateBase64(String text, int size) {
        byte[] png = generatePng(text, size);
        return png != null ? Base64.getEncoder().encodeToString(png) : null;
    }
}
