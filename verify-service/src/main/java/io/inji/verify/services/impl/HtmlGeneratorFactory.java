package io.inji.verify.services.impl;

import io.inji.verify.services.HtmlGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HtmlGeneratorFactory {
    @Autowired
    private Map<String, HtmlGeneratorService> htmlGeneratorServiceMap;

    public HtmlGeneratorService getHtmlGeneratorService(String version) {
        HtmlGeneratorService vcFormatter = htmlGeneratorServiceMap.get(version);
        if (vcFormatter == null) {
            throw new IllegalArgumentException("Invalid instance value: " + version);
        }
        return vcFormatter;
    }
}
