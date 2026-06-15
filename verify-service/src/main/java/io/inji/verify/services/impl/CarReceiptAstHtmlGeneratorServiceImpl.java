package io.inji.verify.services.impl;

import io.inji.verify.services.HtmlGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service("CarReceiptAstHtmlGeneratorServiceImpl")
public class CarReceiptAstHtmlGeneratorServiceImpl implements HtmlGeneratorService {
    @Override
    public String replaceAndGetHtml(Map<String, String> data, String issuerId, String credentialType) {
        String mergedHtml = getCredentialSupportedTemplateString(issuerId, credentialType);
        List<String> sortedKeys = data.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String key : sortedKeys) {
            try {
                String rawValue = data.get(key);
                if (rawValue != null && rawValue.matches("\\$\\{.+}")) {
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, "-");
                    continue;
                }
                if (key.equals("proprietarios")) {
                    String input = data.get(key);
                    ArrayList<HashMap<String, String>> resultList = new ArrayList<>();

                    // Try JSON array format first
                    boolean parsed = false;
                    if (input.trim().startsWith("[")) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            java.util.List<Map<String, Object>> list = mapper.readValue(input, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                            for (Map<String, Object> item : list) {
                                HashMap<String, String> filtered = new HashMap<>();
                                filtered.put("cpfCnpj", String.valueOf(item.getOrDefault("cpfCnpj", "-")));
                                filtered.put("nome", String.valueOf(item.getOrDefault("nome", "-")));
                                resultList.add(filtered);
                            }
                            parsed = true;
                        } catch (Exception e) {
                            log.debug("Not valid JSON, falling back to key=value format");
                        }
                    }

                    if (!parsed) {
                        Pattern mapPattern = Pattern.compile("\\{([^}]+)}");
                        Matcher mapMatcher = mapPattern.matcher(input);
                        while (mapMatcher.find()) {
                            String mapContent = mapMatcher.group(1);
                            HashMap<String, String> map = new HashMap<>();
                            Pattern pairPattern = Pattern.compile("(\\w+)=([^,}]+)");
                            Matcher pairMatcher = pairPattern.matcher(mapContent);
                            while (pairMatcher.find()) {
                                map.put(pairMatcher.group(1).trim(), pairMatcher.group(2).trim());
                            }
                            HashMap<String, String> filtered = new HashMap<>();
                            filtered.put("nome", map.get("nome"));
                            filtered.put("cpfCnpj", map.get("cpfCnpj"));
                            resultList.add(filtered);
                        }
                    }

                    StringBuilder html = new StringBuilder();
                    for (HashMap<String, String> entry : resultList) {
                        String cpf = entry.get("cpfCnpj") != null ? entry.get("cpfCnpj") : "-";
                        String docLabel = cpf.replaceAll("\\D", "").length() == 14 ? "CNPJ" : "CPF";
                        html.append("    <tr>\n");
                        html.append("        <td colspan=\"3\">").append(docLabel).append(": ").append(cpf).append("</td>\n");
                        html.append("        <td colspan=\"3\">Nome: ").append(entry.get("nome")).append("</td>\n");
                        html.append("    </tr>\n");
                    }
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, html.toString());
                }else{
                    mergedHtml = mergedHtml.replace("REPLACEME-->" + key, data.get(key) != null ? data.get(key) : "");
                }
            } catch (IllegalArgumentException ex) {
                log.error("Error while replacing key in template {}", key);
                mergedHtml = mergedHtml.replace("REPLACEME-->" + key, "");
            }
        }

        // Remove matrícula section if all fields are null
        String[] matriculaKeys = {"matricula", "dataMatricula", "livroMatricula", "folhaMatricula"};
        boolean allMatriculaNull = true;
        for (String mk : matriculaKeys) {
            String val = data.get(mk);
            if (val != null && !val.equals("null") && !val.matches("\\$\\{.+}")) {
                allMatriculaNull = false;
                break;
            }
        }
        if (allMatriculaNull) {
            mergedHtml = mergedHtml.replaceAll("(?s)<!--BEGIN_MATRICULA-->.*?<!--END_MATRICULA-->", "");
        }

        return mergedHtml;
    }
}
