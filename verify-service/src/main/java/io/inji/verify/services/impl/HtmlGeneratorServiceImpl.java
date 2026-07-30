package io.inji.verify.services.impl;

import io.inji.verify.services.HtmlGeneratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service("defaultHtmlGeneratorService")
@Primary
public class HtmlGeneratorServiceImpl implements HtmlGeneratorService {
    @Override
    public String replaceAndGetHtml(Map<String, String> data, String credentialType) {
        String mergedHtml = getCredentialSupportedTemplateString(credentialType);
        for (String key : data.keySet()) {
            try {
                String value = data.get(key);
                if (value != null && value.matches("\\$\\{.+}")) {
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, "-");
                } else {
                    mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, value);
                }
            } catch (IllegalArgumentException ex) {
                log.error("Error while replacing key in template {}", key);
                mergedHtml = mergedHtml.replaceAll("REPLACEME-->" + key, "");
            }
        }
        return mergedHtml;
    }
}
