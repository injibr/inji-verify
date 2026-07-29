package io.inji.verify.services.impl;

import io.inji.verify.services.HtmlGeneratorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service("CCIRCredentialHtmlGeneratorServiceImpl")
public class CCIRCredentialHtmlGeneratorServiceImpl implements HtmlGeneratorService {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<String> DATE_FIELDS = Set.of(
            "dataProcessamentoUltimaDeclaracao", "dataLancamento", "dataGeracaoCcir", "dataVencimentoCcir"
    );
    private static final Set<String> DECIMAL_FIELDS = Set.of(
            "areaTotal", "areaMedida", "areaModuloRural", "areaModuloFiscal",
            "numeroModulosRurais", "numeroModulosFiscais", "fracaoMinimaParcelamento",
            "totalAreaRegistrada", "totalAreaPosseJustoTitulo", "totalAreaPosseSimplesOcupacao",
            "areaCertificada", "debitosAnteriores", "taxaServicosCadastrais",
            "valorCobrado", "multa", "juros", "valorTotal"
    );
    private static final DateTimeFormatter OUTPUT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private String formatDate(String value) {
        try {
            if (value.contains(" ")) {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).format(OUTPUT_FORMAT);
            }
            return LocalDate.parse(value).format(OUTPUT_FORMAT);
        } catch (Exception e) {
            return value;
        }
    }

    private String formatCodigoImovel(String codigo) {
        String d = codigo.replaceAll("\\D", "");
        if (d.length() == 13) {
            return d.substring(0,3) + "." + d.substring(3,6) + "." + d.substring(6,9) + "." + d.substring(9,12) + "-" + d.substring(12);
        }
        return codigo;
    }

    private String formatCpf(String value) {
        String d = value.replaceAll("\\D", "");
        if (d.length() == 11) {
            return d.substring(0,3) + "." + d.substring(3,6) + "." + d.substring(6,9) + "-" + d.substring(9);
        }
        return value;
    }

    private String formatDecimal(String value) {
        try {
            String[] parts = value.split("\\.");
            if (parts.length == 1 || parts[1].length() < 2) {
                return String.format("%.2f", Double.parseDouble(value)).replace('.', ',');
            }
            return value.replace('.', ',');
        } catch (NumberFormatException e) {
            return value;
        }
    }

    @Override
    public String replaceAndGetHtml(Map<String, String> data, String credentialType) {
        String mergedHtml = getCredentialSupportedTemplateString(credentialType);
        for (String key : data.keySet()) {
            try {
                String rawValue = data.get(key);
                if (rawValue != null && rawValue.matches("\\$\\{.+}")) {
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, "-");
                    continue;
                }
                if (key.equals("titulares")) {
                    String input = data.get(key);
                    List<Map<String, String>> resultList = mapper.readValue(input, new TypeReference<>() {});

                    StringBuilder html = new StringBuilder();
                    for (Map<String, String> entry : resultList) {
                        html.append("    <tr class=\"titular-row\">\n");
                        html.append("        <td colspan=\"2\"><span class=\"value-large\">").append(formatCpf(entry.get("cpfCnpj"))).append("</span></td>\n");
                        html.append("        <td colspan=\"7\"><span class=\"value-large\">").append(entry.get("nomeTitular")).append("</span></td>\n");
                        html.append("        <td colspan=\"4\"><span class=\"value-large\">").append(entry.get("condicaoTitularidade")).append("</span></td>\n");
                        html.append("        <td colspan=\"2\" class=\"positioned-cell\"><span class=\"value-large bottom-right\">").append(formatDecimal(entry.get("percentualDetencao"))).append("</span></td>\n");
                        html.append("    </tr>\n");
                    }
                    html.append("    <tr><td colspan=\"15\" style=\"border-left:none;border-right:none;border-bottom:1px solid #000;border-top:none;padding:0;height:0;\"></td></tr>\n");
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, html.toString());
                } else if (DECIMAL_FIELDS.contains(key)) {
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, formatDecimal(data.get(key)));
                } else if (key.equals("codigoImovelIncra")) {
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, formatCodigoImovel(data.get(key)));
                } else if (key.equals("cpfCnpj")) {
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, formatCpf(data.get(key)));
                } else if (DATE_FIELDS.contains(key)) {
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, formatDate(data.get(key)));
                } else {
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, data.get(key));
                }
            } catch (Exception ex) {
                log.error("Error while replacing key in template {}", key, ex);
                mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, "");
            }
        }
        mergedHtml = mergedHtml.replaceAll("REPLACEME-->anoExercicio", String.valueOf(Year.now().getValue()));

        // Resolve image paths to absolute classpath URLs so iText can find them without baseUri
        try {
            java.net.URL logoUrl = getClass().getClassLoader().getResource("templates/logo.png");
            if (logoUrl != null) {
                mergedHtml = mergedHtml.replace("src=\"logo.png\"", "src=\"" + logoUrl.toExternalForm() + "\"");
            }
        } catch (Exception ignored) {}

        return mergedHtml;
    }
}
