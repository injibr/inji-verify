package io.inji.verify.services.impl;

import io.inji.verify.services.HtmlGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service("CARReceiptHtmlGeneratorServiceImpl")
public class CARReceiptHtmlGeneratorServiceImpl implements HtmlGeneratorService {

    private static final Set<String> DATE_FIELDS = Set.of("dataCadastro");
    private static final Set<String> DECIMAL_FIELDS = Set.of("moduloFiscal", "areaTotalImovel",
            "areaConsolidada", "areaPreservacaoPermanente", "areaRemanescenteVegetacaoNativa",
            "areaReservaLegal", "areaServidaoAdministrativa", "areaLiquidaImovel", "areaUsoRestrito");
    private static final Set<String> COORD_FIELDS = Set.of("coordenadaImovelX", "coordenadaImovelY");
    private static final DateTimeFormatter OUTPUT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private String formatDate(String value) {
        try {
            return LocalDate.parse(value).format(OUTPUT_FORMAT);
        } catch (Exception e) {
            return value;
        }
    }

    private String formatDecimal(String value) {
        try {
            double d = Double.parseDouble(value);
            return String.format("%.4f", d);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private String formatCoordinate(String value, String key) {
        try {
            double decimal = Double.parseDouble(value);
            String direction;
            if (key.equals("coordenadaImovelY")) {
                direction = decimal >= 0 ? "N" : "S";
            } else {
                direction = decimal >= 0 ? "L" : "O";
            }
            decimal = Math.abs(decimal);
            int degrees = (int) decimal;
            double minutesDecimal = (decimal - degrees) * 60;
            int minutes = (int) minutesDecimal;
            double seconds = (minutesDecimal - minutes) * 60;
            return String.format("%d°%02d'%05.2f'' %s", degrees, minutes, seconds, direction)
                    .replace('.', ',');
        } catch (NumberFormatException e) {
            return value;
        }
    }

    @Override
    public String replaceAndGetHtml(Map<String, String> data, String issuerId, String credentialType) {
        String mergedHtml = getCredentialSupportedTemplateString(issuerId, credentialType);
        List<String> sortedKeys = data.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String key : sortedKeys) {
            try {
                String value = data.get(key);
                if (value == null || value.equals("null")) {
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, "-");
                } else if (key.equals("proprietarios")) {
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, buildProprietariosHtml(value));
                } else if (key.equals("geoImovel")) {
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, buildSvgFromPolygon(value));
                } else if (COORD_FIELDS.contains(key)) {
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, formatCoordinate(value, key));
                } else if (DATE_FIELDS.contains(key)) {
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, formatDate(value));
                } else if (DECIMAL_FIELDS.contains(key)) {
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, formatDecimal(value));
                } else {
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, value);
                }
            } catch (Exception ex) {
                log.error("Error while replacing key in template {}", key, ex);
                mergedHtml = mergedHtml.replace("REPLACEME-->" + key, "");
            }
        }
        return mergedHtml;
    }

    private String buildProprietariosHtml(String input) {
        Pattern mapPattern = Pattern.compile("\\{([^}]+)}");
        Matcher mapMatcher = mapPattern.matcher(input);

        ArrayList<HashMap<String, String>> resultList = new ArrayList<>();
        while (mapMatcher.find()) {
            String mapContent = mapMatcher.group(1);
            HashMap<String, String> map = new HashMap<>();
            Pattern pairPattern = Pattern.compile("(\\w+)=([^,}]+)");
            Matcher pairMatcher = pairPattern.matcher(mapContent);
            while (pairMatcher.find()) {
                map.put(pairMatcher.group(1).trim(), pairMatcher.group(2).trim());
            }
            resultList.add(map);
        }

        StringBuilder html = new StringBuilder();
        for (HashMap<String, String> entry : resultList) {
            String cpf = formatCpf(Objects.toString(entry.get("cpfCnpj"), "-"));
            String nome = Objects.toString(entry.get("nome"), "-");
            html.append("    <tr>\n");
            html.append("        <td colspan=\"3\">CPF: ").append(cpf).append("</td>\n");
            html.append("        <td colspan=\"3\">Nome: ").append(nome).append("</td>\n");
            html.append("    </tr>\n");
        }
        return html.toString();
    }

    private String formatCpf(String value) {
        String d = value.replaceAll("\\D", "");
        if (d.length() == 11) {
            return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
        }
        return value;
    }

    private String buildSvgFromPolygon(String wkt) {
        try {
            Pattern coordPattern = Pattern.compile("-?\\d+\\.\\d+\\s+-?\\d+\\.\\d+");
            Matcher matcher = coordPattern.matcher(wkt);

            List<double[]> coords = new ArrayList<>();
            while (matcher.find()) {
                String[] parts = matcher.group().split("\\s+");
                coords.add(new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])});
            }

            if (coords.isEmpty()) return wkt;

            double minLon = coords.stream().mapToDouble(c -> c[0]).min().orElse(0);
            double maxLon = coords.stream().mapToDouble(c -> c[0]).max().orElse(0);
            double minLat = coords.stream().mapToDouble(c -> c[1]).min().orElse(0);
            double maxLat = coords.stream().mapToDouble(c -> c[1]).max().orElse(0);

            double centerLon = (minLon + maxLon) / 2;
            double centerLat = (minLat + maxLat) / 2;

            int imgSize = 512;
            int imgHeight = 250;
            int tileSize = 256;

            // Calculate zoom to fit polygon in image with margin
            double latDiff = maxLat - minLat;
            double lonDiff = maxLon - minLon;
            double maxDiff = Math.max(latDiff, lonDiff);

            // Calculate zoom so polygon fills ~60% of the image
            int zoom = 1;
            for (int z = 18; z >= 1; z--) {
                double n = 1 << z;
                double pixelSpanLon = (lonDiff / 360.0) * n * tileSize;
                double latRadMin = Math.toRadians(minLat);
                double latRadMax = Math.toRadians(maxLat);
                double yMin = (1 - Math.log(Math.tan(latRadMax) + 1 / Math.cos(latRadMax)) / Math.PI) / 2 * n * tileSize;
                double yMax = (1 - Math.log(Math.tan(latRadMin) + 1 / Math.cos(latRadMin)) / Math.PI) / 2 * n * tileSize;
                double pixelSpanLat = Math.abs(yMax - yMin);
                if (pixelSpanLon < imgSize * 0.6 && pixelSpanLat < imgHeight * 0.6) {
                    zoom = z;
                    break;
                }
            }
            double n = 1 << zoom;

            // Center tile pixel coordinates (global)
            double centerPixelX = ((centerLon + 180) / 360) * n * tileSize;
            double centerPixelY = (1 - Math.log(Math.tan(Math.toRadians(centerLat)) + 1 / Math.cos(Math.toRadians(centerLat))) / Math.PI) / 2 * n * tileSize;

            // Bounding box in pixels for our image
            double imgLeft = centerPixelX - imgSize / 2.0;
            double imgTop = centerPixelY - imgHeight / 2.0;

            // Determine which tiles we need
            int tileXmin = (int) Math.floor(imgLeft / tileSize);
            int tileXmax = (int) Math.floor((imgLeft + imgSize) / tileSize);
            int tileYmin = (int) Math.floor(imgTop / tileSize);
            int tileYmax = (int) Math.floor((imgTop + imgHeight) / tileSize);

            // Create final image
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(imgSize, imgHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(new java.awt.Color(240, 240, 240));
            g2d.fillRect(0, 0, imgSize, imgHeight);

            // Download and draw tiles
            for (int tx = tileXmin; tx <= tileXmax; tx++) {
                for (int ty = tileYmin; ty <= tileYmax; ty++) {
                    byte[] tileBytes = fetchSingleTile(zoom, tx, ty);
                    if (tileBytes != null) {
                        java.awt.image.BufferedImage tileImage = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(tileBytes));
                        int drawX = (int) (tx * tileSize - imgLeft);
                        int drawY = (int) (ty * tileSize - imgTop);
                        g2d.drawImage(tileImage, drawX, drawY, null);
                    }
                }
            }

            // Convert polygon coordinates to pixel positions on our image
            int[] xPoints = new int[coords.size()];
            int[] yPoints = new int[coords.size()];
            for (int i = 0; i < coords.size(); i++) {
                double lon = coords.get(i)[0];
                double lat = coords.get(i)[1];
                double px = ((lon + 180) / 360) * n * tileSize - imgLeft;
                double py = (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * n * tileSize - imgTop;
                xPoints[i] = (int) px;
                yPoints[i] = (int) py;
            }

            // Fill polygon
            g2d.setColor(new java.awt.Color(144, 238, 144, 100));
            g2d.fillPolygon(xPoints, yPoints, coords.size());

            // Stroke polygon
            g2d.setColor(new java.awt.Color(0, 100, 0));
            g2d.setStroke(new java.awt.BasicStroke(2));
            g2d.drawPolygon(xPoints, yPoints, coords.size());

            g2d.dispose();

            // Convert to base64 PNG
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", baos);
            String base64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());

            return "<img src=\"data:image/png;base64," + base64 + "\" style=\"width:100%;\" />";
        } catch (Exception e) {
            log.error("Error building map from polygon", e);
            return wkt;
        }
    }

    private byte[] fetchSingleTile(int zoom, int x, int y) {
        try {
            // Esri World Imagery (satellite)
            String url = String.format("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/%d/%d/%d", zoom, y, x);

            javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    }
            };
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new java.security.SecureRandom());

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .sslContext(sslContext)
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("User-Agent", "InjiVerify/1.0")
                    .GET()
                    .build();
            java.net.http.HttpResponse<byte[]> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                return response.body();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
