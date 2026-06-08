package io.inji.verify.services.impl;

import io.inji.verify.services.HtmlGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
@Service("CARDocumentHtmlGeneratorServiceImpl")
public class CARDocumentHtmlGeneratorServiceImpl implements HtmlGeneratorService {

    private static final Set<String> COORD_FIELDS = Set.of("coordenadaImovelX", "coordenadaImovelY");
    private static final Set<String> DECIMAL_FIELDS = Set.of("areaTotalImovel", "quantidadeModulosFiscais",
            "areaRemanescenteVegetacaoNativa", "areaConsolidada", "areaServidaoAdministrativa",
            "areaReservaLegalAverbada", "areaReservaLegalAprovadaNaoAverbada", "areaReservaLegalProposta",
            "areaReservaLegalDeclaradaProprietarioPossuidor", "areaPreservacaoPermanente",
            "areaPreservacaoPermanenteAreaRuralConsolidada", "areaPreservacaoPermanenteAreaRemanescenteVegetacaoNativa",
            "areaUsoRestrito", "areaUsoRestritoDeclividade", "areaReservaLegalExcedentePassivo",
            "areaReservaLegalRecompor", "areaPreservacaoPermanenteRecompor", "areaUsoRestritoRecompor");

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
        String[] multiKeys = {
                "sobreposicoesAreasEmbargadas",
                "sobreposicoesUnidadeConservacao",
                "sobreposicoesTerraIndigena"
        };
        String mergedHtml = getCredentialSupportedTemplateString(issuerId, credentialType);
        List<String> sortedKeys = data.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String key : sortedKeys) {
            try {
                if (key.equals("sobreposicoesAreasEmbargadas") || key.equals("sobreposicoesUnidadeConservacao") || key.equals("sobreposicoesTerraIndigena")) {
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
                        filtered.put("tema", Objects.isNull(map.get("tema"))?"":map.get("tema"));
                        filtered.put("fase", Objects.isNull(map.get("fase"))?"-":map.get("fase"));
                        filtered.put("descricao", Objects.isNull(map.get("descricao"))?"":map.get("descricao"));
                        filtered.put("processamento", Objects.isNull(map.get("processamento"))?"":map.get("processamento"));
                        filtered.put("areaSobreposicao",Objects.isNull(map.get("areaSobreposicao"))?"":map.get("areaSobreposicao"));
                        filtered.put("percentualSobreposicao",Objects.isNull(map.get("percentualSobreposicao"))?"":map.get("percentualSobreposicao"));
                        resultList.add(filtered);
                    }

                    StringBuilder html = new StringBuilder();
                    for (HashMap<String, String> entry : resultList) {
                        html.append("    <tbody>\n");
                        html.append("    <tr>\n");
                        html.append("        <td style=\"padding: 6px;\">").append(entry.get("tema")).append("</td>\n");
                        html.append("        <td style=\"padding: 6px;\">").append(entry.get("fase")).append("</td>\n");
                        html.append("        <td style=\"padding: 6px;\">").append(entry.get("descricao")).append("</td>\n");
                        html.append("        <td style=\"padding: 6px;\">").append(entry.get("processamento")).append("</td>\n");
                        html.append("        <td style=\"padding: 6px; text-align: center;\">").append(entry.get("areaSobreposicao")).append("</td>\n");
                        html.append("        <td style=\"padding: 6px; text-align: center;\">").append(entry.get("percentualSobreposicao")).append("</td>\n");
                        html.append("    </tr>\n");
                        html.append("    </tbody>\n");
                    }
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, html.toString());

                } else if (COORD_FIELDS.contains(key)) {
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, formatCoordinate(data.get(key), key));
                } else if (key.equals("situacaoImovel")) {
                    String value = data.get(key);
                    String descricao = switch (value) {
                        case "AT" -> "Ativo";
                        case "PE" -> "Pendente";
                        case "SU" -> "Suspenso";
                        case "CA" -> "Cancelado";
                        default -> value;
                    };
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, descricao);
                } else if (DECIMAL_FIELDS.contains(key)) {
                    String value = data.get(key);
                    if (value != null && !value.equals("null")) {
                        try {
                            double d = Double.parseDouble(value);
                            java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("pt", "BR"));
                            nf.setMinimumFractionDigits(2);
                            nf.setMaximumFractionDigits(2);
                            mergedHtml = mergedHtml.replace("REPLACEME-->" + key, nf.format(d));
                        } catch (NumberFormatException e) {
                            mergedHtml = mergedHtml.replace("REPLACEME-->" + key, value.replace('.', ','));
                        }
                    } else {
                        mergedHtml = mergedHtml.replace("REPLACEME-->" + key, "-");
                    }
                } else{
                    String value = data.get(key);
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, (value != null && !value.equals("null")) ? value : "-");
                }
            } catch (IllegalArgumentException ex) {
                log.error("Error while replacing key in template {}", key);
                mergedHtml = mergedHtml.replace("REPLACEME-->" + key, "");
            }
        }
        for (String mk : multiKeys) {
            if (!data.containsKey(mk)) {
                mergedHtml = mergedHtml.replace("REPLACEME-->" + mk, "");
            }
        }
        mergedHtml = mergedHtml.replace("REPLACEME-->dataGeracao",
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        // Dynamic page numbering
        int totalPages = mergedHtml.split("class=\"page\"").length - 1;
        int pageNum = 1;
        while (mergedHtml.contains("REPLACEME-->paginaInfo")) {
            mergedHtml = mergedHtml.replaceFirst("REPLACEME-->paginaInfo", pageNum + " de " + totalPages);
            pageNum++;
        }

        return mergedHtml;
    }
}
