package io.inji.verify.services;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;


public interface HtmlGeneratorService {
    String replaceAndGetHtml(Map<String,String> data, String issuerId, String credentialType);

    /**
     * Retrieves the HTML template string for a given issuer ID and credential type.
     * If a specific template is not found, it falls back to a default template.
     *
     * @param issuerId       the ID of the issuer
     * @param credentialType the type of the credential
     * @return the HTML template string
     */
    default String getCredentialSupportedTemplateString(String issuerId, String credentialType) {
        String templateFileName = String.format("%s-%s-template.html", issuerId, credentialType);
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
