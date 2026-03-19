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
@Service("CCIRCredentialHtmlGeneratorServiceImpl")
public class CCIRCredentialHtmlGeneratorServiceImpl implements HtmlGeneratorService {
    @Override
    public String replaceAndGetHtml(Map<String, String> data, String issuerId, String credentialType) {
        String mergedHtml = getCredentialSupportedTemplateString(issuerId, credentialType);
        for (String key : data.keySet()) {
            try {
                if (key.equals("titulares")) {
                    String input = data.get(key);
                    Pattern mapPattern = Pattern.compile("\\{([^}]+)}");
                    Matcher mapMatcher = mapPattern.matcher(input);

                    ArrayList<HashMap<String, String>> resultList = new ArrayList<>();
                    while (mapMatcher.find()) {
                        String mapContent = mapMatcher.group(1);
                        HashMap<String, String> map = new HashMap<>();
                        Pattern pairPattern = Pattern.compile("(\\w+)=([^,]+)(?:,|$)");
                        Matcher pairMatcher = pairPattern.matcher(mapContent);
                        while (pairMatcher.find()) {
                            map.put(pairMatcher.group(1).trim(), pairMatcher.group(2).trim());
                        }
                        resultList.add(map);
                    }

                    StringBuilder html = new StringBuilder();
                    for (HashMap<String, String> entry : resultList) {
                        html.append("    <tr>\n");
                        html.append("        <td colspan=\"2\"><span class=\"value-large\">").append(entry.get("cpfCnpj")).append("</span></td>\n");
                        html.append("        <td colspan=\"7\"><span class=\"value-large\">").append(entry.get("nomeTitular")).append("</span></td>\n");
                        html.append("        <td colspan=\"4\"><span class=\"value-large\">").append(entry.get("condicaoTitularidade")).append("</span></td>\n");
                        html.append("        <td colspan=\"2\" class=\"positioned-cell\"><span class=\"value-large bottom-right\">").append(entry.get("percentualDetencao")).append("</span></td>\n");
                        html.append("    </tr>\n");
                    }
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, html.toString());
                } else {
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, data.get(key));
                }
            } catch (IllegalArgumentException ex) {
                log.error("Error while replacing key in template {}", key);
                mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, "");
            }
        }
        return mergedHtml;
    }
}
