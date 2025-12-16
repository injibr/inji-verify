package io.inji.verify.services.impl;

import io.inji.verify.services.HtmlGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service("CARDocumentHtmlGeneratorServiceImpl")
public class CARDocumentHtmlGeneratorServiceImpl implements HtmlGeneratorService {
    @Override
    public String replaceAndGetHtml(Map<String, String> data, String issuerId, String credentialType) {
        String[] multiKeys = {
                "sobreposicoesAreasEmbargadas",
                "sobreposicoesUnidadeConservacao",
                "sobreposicoesTerraIndigena"
        };
        String mergedHtml = getCredentialSupportedTemplateString(issuerId, credentialType);
        for (String key : data.keySet()) {
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
                        html.append("        <td style=\"padding: 6px;\">").append(entry.get("areaSobreposicao")).append("</td>\n");
                        html.append("        <td style=\"padding: 6px;\">").append(entry.get("percentualSobreposicao")).append("</td>\n");
                        html.append("    </tr>\n");
                        html.append("    </tbody>\n");
                    }
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, html.toString());

                } else{
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, data.get(key));
                }
            } catch (IllegalArgumentException ex) {
                log.error("Error while replacing key in template {}", key);
                // If there's an error (e.g., special characters in the value), remove the placeholder
                mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, "");
            }
        }
        for (String mk : multiKeys) {
            if (!data.containsKey(mk)) {
                mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + mk, "");
            }
        }
        return mergedHtml;
    }
}
