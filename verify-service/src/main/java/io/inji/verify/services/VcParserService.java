package io.inji.verify.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;

/**
 * Service interface for parsing Verifiable Credentials (VC).
 */
public interface VcParserService {
    Map<String, String> extractCredentialSubject(String jsonInput) throws JsonProcessingException;
    String getValueFromVcMetadata(String jsonInput,String vcMetadataNode) throws JsonProcessingException;
}
