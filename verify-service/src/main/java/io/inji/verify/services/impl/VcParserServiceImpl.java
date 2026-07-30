package io.inji.verify.services.impl;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.inji.verify.services.VcParserService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Service to parse Verifiable Credentials (VC) JSON and extract specific information.
 */
@Service
public class VcParserServiceImpl implements VcParserService {
    /**
     * Extracts the credentialSubject from the first verifiableCredential in the input JSON.
     *
     * @param jsonInput The input JSON string containing verifiable credentials.
     * @return A map representing the credentialSubject.
     * @throws JsonProcessingException If there is an error processing the JSON.
     */
    public Map<String, String> extractCredentialSubject(String jsonInput, int vcNumber) throws JsonProcessingException {

        JsonNode credentialSubjectNode = getVerifiableCredentialNode(jsonInput, vcNumber)
                .path("credentialSubject");

        if (credentialSubjectNode.isMissingNode()) {
            throw new IllegalArgumentException("credentialSubject not found in VC");
        }

        // Convert to Map<String, String>
        Map<String, String> result = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = credentialSubjectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            result.put(entry.getKey(), entry.getValue().asText());
        }

        return result;
    }

    /**
     * Extracts the credential type from the verifiableCredential at the given index.
     *
     * @param jsonInput The input JSON string containing verifiable credentials.
     * @param vcNumber The index of the verifiable credential.
     * @return The extracted credential type as a String.
     * @throws JsonProcessingException If there is an error processing the JSON.
     */
    @Override
    public String getTypesInVerifiableCredential(String jsonInput, int vcNumber) throws JsonProcessingException {

        JsonNode typeNode = getVerifiableCredentialNode(jsonInput, vcNumber)
                .path("type");

        if (typeNode.isMissingNode()) {
            throw new IllegalArgumentException("type not found in VC");
        }

        String extractedType = null;
        if (typeNode.isArray()) {
            for (JsonNode type : typeNode) {
                String typeValue = type.asText();
                if (!"VerifiableCredential".equals(typeValue)) {
                    extractedType = typeValue;
                    break;
                }
            }
        }

        return extractedType;
    }

    private JsonNode getVerifiableCredentialNode(String jsonInput, int vcNumber) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(jsonInput);

        JsonNode vcArrayNode = rootNode.get("verifiableCredential");
        if (vcArrayNode == null || !vcArrayNode.isArray() || vcArrayNode.isEmpty()) {
            throw new IllegalArgumentException("verifiableCredential array is missing or empty");
        }

        JsonNode vcElement = vcArrayNode.get(vcNumber);
        if (vcElement == null) {
            throw new IllegalArgumentException("verifiableCredential[" + vcNumber + "] not found");
        }
        if (!vcElement.isObject()) {
            throw new IllegalArgumentException("verifiableCredential[" + vcNumber + "] is not a JSON object — expected W3C format");
        }

        return vcElement;
    }

    @Override
    public int getTotalNumberOfVc(String jsonInput) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(jsonInput);

        JsonNode vcArrayNode = rootNode.get("verifiableCredential");
        if (vcArrayNode == null || !vcArrayNode.isArray() || vcArrayNode.isEmpty()) {
            throw new IllegalArgumentException("verifiableCredential array is missing or empty");
        }

        return vcArrayNode.size();
    }
}
