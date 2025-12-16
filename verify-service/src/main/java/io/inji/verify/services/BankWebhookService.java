package io.inji.verify.services;

import io.inji.verify.dto.submission.VPTokenResultDto;

import java.io.ByteArrayInputStream;
import java.util.Map;


/**
 * Service interface for handling bank webhook operations.
 */
public interface BankWebhookService {
    void callWebhook(Map<String, ByteArrayInputStream> pdfs,
                     VPTokenResultDto result,
                     String webhookUrl,
                     String apiKey,
                     String tokenUrl,
                     String tokenUri,
                     String webhookUri);
}
