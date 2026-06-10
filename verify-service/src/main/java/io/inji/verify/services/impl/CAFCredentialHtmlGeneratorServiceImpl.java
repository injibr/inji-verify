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

                        // Try JSON format: "key":"value"
                        Pattern jsonPattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");
                        Matcher jsonMatcher = jsonPattern.matcher(mapContent);
                        boolean foundJson = false;
                        while (jsonMatcher.find()) {
                            map.put(jsonMatcher.group(1).trim(), jsonMatcher.group(2).trim());
                            foundJson = true;
                        }

                        // Fallback: key=value format
                        if (!foundJson) {
                            Pattern pairPattern = Pattern.compile("(\\w+)=([^,]+)(?:,|$)");
                            Matcher pairMatcher = pairPattern.matcher(mapContent);
                            while (pairMatcher.find()) {
                                map.put(pairMatcher.group(1).trim(), pairMatcher.group(2).trim());
                            }
                        }

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

                    Pattern mapPattern = Pattern.compile("\\{([^}]+)}");
                    Matcher mapMatcher = mapPattern.matcher(input);

                    ArrayList<HashMap<String, String>> resultList = new ArrayList<>();

                    while (mapMatcher.find()) {
                        String mapContent = mapMatcher.group(1);
                        HashMap<String, String> map = new HashMap<>();

                        // Try JSON format
                        Pattern jsonPattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");
                        Matcher jsonMatcher = jsonPattern.matcher(mapContent);
                        boolean foundJson = false;
                        while (jsonMatcher.find()) {
                            map.put(jsonMatcher.group(1).trim(), jsonMatcher.group(2).trim());
                            foundJson = true;
                        }

                        // Fallback: key=value
                        if (!foundJson) {
                            Pattern pairPattern = Pattern.compile("(\\w+)=([^,]+)(?:,|$)");
                            Matcher pairMatcher = pairPattern.matcher(mapContent);
                            while (pairMatcher.find()) {
                                map.put(pairMatcher.group(1).trim(), pairMatcher.group(2).trim());
                            }
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
                } else if (key.equals("cnpjEntidade") || key.equals("cnpj")) {
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
        String qrCodeBase64 = io.inji.verify.utils.QrCodeGenerator.generateBase64("https://caf.mda.gov.br/consulta-publica/ufpa", 100);
        if (qrCodeBase64 != null) {
            mergedHtml = mergedHtml.replace("REPLACEME-->qrCode", "<img src=\"data:image/png;base64," + qrCodeBase64 + "\" width=\"100\" height=\"100\" />");
        } else {
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


}
