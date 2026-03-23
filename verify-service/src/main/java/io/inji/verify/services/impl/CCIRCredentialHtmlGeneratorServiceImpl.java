package io.inji.verify.services.impl;

import io.inji.verify.services.HtmlGeneratorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service("CCIRCredentialHtmlGeneratorServiceImpl")
public class CCIRCredentialHtmlGeneratorServiceImpl implements HtmlGeneratorService {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String replaceAndGetHtml(Map<String, String> data, String issuerId, String credentialType) {
        String mergedHtml = getCredentialSupportedTemplateString(issuerId, credentialType);
        for (String key : data.keySet()) {
            try {
                if (key.equals("titulares")) {
                    String input = data.get(key);
                    List<Map<String, String>> resultList = mapper.readValue(input, new TypeReference<>() {});

                    StringBuilder html = new StringBuilder();
                    for (Map<String, String> entry : resultList) {
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
            } catch (Exception ex) {
                log.error("Error while replacing key in template {}", key, ex);
                mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, "");
            }
        }
        return mergedHtml;
    }
}
