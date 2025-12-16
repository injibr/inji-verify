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
                        html.append("    <tr>\n");
                        html.append("        <td>").append(entry.get("nome")).append("</td>\n");
                        html.append("        <td>").append(entry.get("cpf")).append("</td>\n");
                        html.append("        <td>").append(entry.get("parentesco")).append("</td>\n");
                        html.append("        <td>").append(entry.get("responsavelUfpa")).append("</td>\n");
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
                        mergedHtml = mergedHtml.replaceAll("REPLACEME-->condicaoPosse", entry.get("condicaoPosse"));
                        mergedHtml = mergedHtml.replaceAll("REPLACEME-->tamanho", entry.get("tamanho"));
                        mergedHtml = mergedHtml.replaceAll("REPLACEME-->municipio", entry.get("municipio"));
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
        return mergedHtml;
    }
}
