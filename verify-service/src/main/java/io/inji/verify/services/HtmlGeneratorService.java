package io.inji.verify.services;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;


public interface HtmlGeneratorService {
    String replaceAndGetHtml(Map<String,String> data, String credentialType);

    /**
     * Retrieves the HTML template string for a given credential type.
     * If a specific template is not found, it falls back to a default template.
     *
     * @param credentialType the type of the credential
     * @return the HTML template string
     */
    default String getCredentialSupportedTemplateString(String credentialType) {
        String templateFileName = String.format("%s-template.html", credentialType);
        Path basePath = Paths.get("src/main/resources/templates").toAbsolutePath().normalize();
        Path resolvedPath = basePath.resolve(templateFileName).normalize();

        if (!resolvedPath.startsWith(basePath)) {
            throw new SecurityException("Attempted path traversal attack: " + resolvedPath);
        }
        try {
            return new String(
                    Objects.requireNonNull(PdfService.class.getClassLoader()
                                    .getResourceAsStream("templates/" + templateFileName))
                            .readAllBytes()
            );
        } catch (IOException e) {
            return "";
        }
    }
}
