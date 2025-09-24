package io.inji.verify.services.impl;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.inji.verify.services.VcParserService;
import org.apache.commons.lang.StringEscapeUtils;
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

        // Navigate to credentialSubject
        JsonNode credentialSubjectNode = getVerifiableCredentialNode(jsonInput, vcNumber)
                .path("verifiableCredential")
                .path("credential")
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
     * Extracts a specific value from the vcMetadata of the first verifiableCredential in the input JSON.
     *
     * @param jsonInput The input JSON string containing verifiable credentials.
     * @param vcMetadataNode The specific node in vcMetadata to extract (e.g., "issuer").
     * @return The extracted value as a String.
     * @throws JsonProcessingException If there is an error processing the JSON.
     */
    public String getValueFromVcMetadata(String jsonInput,String vcMetadataNode,int vcNumber) throws JsonProcessingException {

        // Navigate to vcMetadata -> issuer
        JsonNode issuerNode = getVerifiableCredentialNode(jsonInput, vcNumber)
                .path("vcMetadata")
                .path(vcMetadataNode);

        if (issuerNode.isMissingNode()) {
            throw new IllegalArgumentException("issuer not found in vcMetadata");
        }

        return issuerNode.asText();
    }

    /**
     * Extracts a specific value from the vcMetadata of the first verifiableCredential in the input JSON.
     *
     * @param jsonInput The input JSON string containing verifiable credentials.
     * @return The extracted value as a String.
     * @throws JsonProcessingException If there is an error processing the JSON.
     */
    @Override
    public String getTypesInVerifiableCredential(String jsonInput,int vcNumber) throws JsonProcessingException {

        // Navigate to vcMetadata -> issuer
        JsonNode issuerNode = getVerifiableCredentialNode(jsonInput, vcNumber)
                .path("verifiableCredential")
                .path("credential")
                .path("type");

        if (issuerNode.isMissingNode()) {
            throw new IllegalArgumentException("issuer not found in vcMetadata");
        }

        String extractedType = null;
        if (issuerNode.isArray()) {
            for (JsonNode typeNode : issuerNode) {
                String typeValue = typeNode.asText();
                if (!"VerifiableCredential".equals(typeValue)) {
                    extractedType = typeValue;
                    break;
                }
            }
        }

        return extractedType;
    }

    private String getCorrectJsonString(String json){
        return json.replace("\t", "\\t");
    }

    private JsonNode getVerifiableCredentialNode(String jsonInput, int vcNumber) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        // Parse root JSON
        JsonNode rootNode = mapper.readTree(getCorrectJsonString(jsonInput));

        JsonNode vcArrayNode = rootNode.get("verifiableCredential");
        if (vcArrayNode == null || !vcArrayNode.isArray() || vcArrayNode.isEmpty()) {
            throw new IllegalArgumentException("verifiableCredential array is missing or empty");
        }

        // Get the first element from "verifiableCredential" array
        String vcEscaped = rootNode.get("verifiableCredential").get(vcNumber).asText();
        // Unescape the string to get valid JSON
        String vcJson = StringEscapeUtils.unescapeJava(vcEscaped);
        // Parse the inner JSON
       return mapper.readTree(vcJson);

    }

    @Override
    public int getTotalNumberOfVc(String jsonInput) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        // Parse root JSON
        JsonNode rootNode = mapper.readTree(getCorrectJsonString(jsonInput));

        JsonNode vcArrayNode = rootNode.get("verifiableCredential");
        if (vcArrayNode == null || !vcArrayNode.isArray() || vcArrayNode.isEmpty()) {
            throw new IllegalArgumentException("verifiableCredential array is missing or empty");
        }

        return vcArrayNode.size();
    }
}
