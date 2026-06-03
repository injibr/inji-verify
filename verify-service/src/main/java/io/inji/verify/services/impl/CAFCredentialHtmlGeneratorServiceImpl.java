package io.inji.verify.services.impl;

import io.inji.verify.services.HtmlGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service("CAFCredentialHtmlGeneratorServiceImpl")
public class CAFCredentialHtmlGeneratorServiceImpl implements HtmlGeneratorService {
    @Override
    public String replaceAndGetHtml(Map<String, String> data, String issuerId, String credentialType) {
        String mergedHtml = getCredentialSupportedTemplateString(issuerId, credentialType);
        for (String key : data.keySet()) {
            try {
                if (key.equals("membros")) {
                    String input = data.get(key);

                    // Pattern to extract each map { ... }
                    Pattern mapPattern = Pattern.compile("\\{([^}]+)}");
                    Matcher mapMatcher = mapPattern.matcher(input);

                    ArrayList<HashMap<String, String>> resultList = new ArrayList<>();

                    while (mapMatcher.find()) {
                        String mapContent = mapMatcher.group(1);
                        HashMap<String, String> map = new HashMap<>();

                        Pattern pairPattern = Pattern.compile("(\\w+)=([^,]+)(?:,|$)");
                        Matcher pairMatcher = pairPattern.matcher(mapContent);

                        while (pairMatcher.find()) {
                            String matcherKey = pairMatcher.group(1).trim();
                            String value = pairMatcher.group(2).trim();
                            map.put(matcherKey, value);
                        }

                        // Extract only nome and cpfCnpj
                        HashMap<String, String> filtered = new HashMap<>();
                        filtered.put("nome", map.get("nome"));
                        filtered.put("cpf", map.get("cpf"));
                        filtered.put("parentesco", map.get("parentesco"));
                        filtered.put("responsavelUfpa", map.get("responsavelUfpa"));
                        resultList.add(filtered);
                    }

                    StringBuilder html = new StringBuilder();
                    for (HashMap<String, String> entry : resultList) {
                        String responsavel = "true".equals(entry.get("responsavelUfpa")) ? "Sim" : "Não";
                        String cpfFormatado = formatCpfMasked(entry.get("cpf"));
                        html.append("    <tr>\n");
                        html.append("        <td>").append(entry.get("nome")).append("</td>\n");
                        html.append("        <td>").append(cpfFormatado).append("</td>\n");
                        html.append("        <td>").append(entry.get("parentesco")).append("</td>\n");
                        html.append("        <td>").append(responsavel).append("</td>\n");
                        html.append("    </tr>\n");
                    }
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, html.toString());
                } else if (key.equals("areas")) {
                    String input = data.get(key);

                    // Pattern to extract each map { ... }
                    Pattern mapPattern = Pattern.compile("\\{([^}]+)}");
                    Matcher mapMatcher = mapPattern.matcher(input);

                    ArrayList<HashMap<String, String>> resultList = new ArrayList<>();

                    while (mapMatcher.find()) {
                        String mapContent = mapMatcher.group(1);
                        HashMap<String, String> map = new HashMap<>();

                        // Pattern to extract key=value pairs
                        Pattern pairPattern = Pattern.compile("(\\w+)=([^,]+)(?:,|$)");
                        Matcher pairMatcher = pairPattern.matcher(mapContent);

                        while (pairMatcher.find()) {
                            String matcherKey = pairMatcher.group(1).trim();
                            String value = pairMatcher.group(2).trim();
                            map.put(matcherKey, value);
                        }

                        HashMap<String, String> filtered = new HashMap<>();
                        filtered.put("condicaoPosse", map.get("condicaoPosse"));
                        filtered.put("tamanho", map.get("tamanho"));
                        filtered.put("municipio", map.get("municipio"));
                        resultList.add(filtered);
                    }

                    StringBuilder html = new StringBuilder();
                    for (HashMap<String, String> entry : resultList) {
                        String tamanho = entry.get("tamanho") != null ? entry.get("tamanho").replace('.', ',') : "-";
                        String tamanhoM2 = "-";
                        try {
                            double ha = Double.parseDouble(entry.get("tamanho"));
                            java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("pt", "BR"));
                            nf.setMinimumFractionDigits(2);
                            nf.setMaximumFractionDigits(2);
                            tamanhoM2 = nf.format(ha * 10000);
                        } catch (Exception ignored) {}
                        mergedHtml = mergedHtml.replace("REPLACEME-->condicaoPosse", entry.get("condicaoPosse") != null ? entry.get("condicaoPosse") : "-");
                        mergedHtml = mergedHtml.replace("REPLACEME-->tamanhoM2", tamanhoM2);
                        mergedHtml = mergedHtml.replace("REPLACEME-->tamanho", tamanho);
                        mergedHtml = mergedHtml.replace("REPLACEME-->municipio", entry.get("municipio") != null ? entry.get("municipio") : "-");
                    }
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, html.toString());
                } else if (key.equals("cnpjEntidade")) {
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, formatCnpj(data.get(key)));
                } else{
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, data.get(key));
                }
            } catch (IllegalArgumentException ex) {
                log.error("Error while replacing key in template {}", key);
                // If there's an error (e.g., special characters in the value), remove the placeholder
                mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, "");
            }
        }

        // Generate QR Code
        try {
            String qrCodeBase64 = generateQrCodeBase64("https://caf.mda.gov.br/consulta-publica/ufpa", 80);
            mergedHtml = mergedHtml.replace("REPLACEME-->qrCode", "<img src=\"data:image/png;base64," + qrCodeBase64 + "\" width=\"80\" height=\"80\" />");
        } catch (Exception e) {
            mergedHtml = mergedHtml.replace("REPLACEME-->qrCode", "");
        }

        // Resolve logo paths
        try {
            java.net.URL logoUrl = getClass().getClassLoader().getResource("templates/logo.png");
            if (logoUrl != null) {
                mergedHtml = mergedHtml.replace("src=\"logo.png\"", "src=\"" + logoUrl.toExternalForm() + "\"");
            }
            java.net.URL cafLogoUrl = getClass().getClassLoader().getResource("templates/caf-logo-new.png");
            if (cafLogoUrl != null) {
                mergedHtml = mergedHtml.replace("src=\"caf-logo-new.png\"", "src=\"" + cafLogoUrl.toExternalForm() + "\"");
            }
        } catch (Exception ignored) {}

        // Dynamic document emission date
        mergedHtml = mergedHtml.replace("REPLACEME-->dataGeracaoDocumento",
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

        return mergedHtml;
    }

    private String formatCpfMasked(String value) {
        if (value == null) return "-";
        String d = value.replaceAll("\\D", "");
        if (d.length() == 11) {
            return d.substring(0, 3) + ".***.**" + "*-" + d.substring(9);
        }
        return value;
    }

    private String formatCnpj(String value) {
        if (value == null) return "-";
        String d = value.replaceAll("\\D", "");
        if (d.length() == 14) {
            return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/" + d.substring(8, 12) + "-" + d.substring(12);
        }
        return value;
    }

    private String generateQrCodeBase64(String text, int size) throws Exception {
        String url = "https://api.qrserver.com/v1/create-qr-code/?data=" + java.net.URLEncoder.encode(text, "UTF-8") + "&size=" + size + "x" + size + "&format=png";

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
                .GET()
                .build();
        java.net.http.HttpResponse<byte[]> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            return java.util.Base64.getEncoder().encodeToString(response.body());
        }
        throw new RuntimeException("Failed to generate QR code: " + response.statusCode());
    }
}
