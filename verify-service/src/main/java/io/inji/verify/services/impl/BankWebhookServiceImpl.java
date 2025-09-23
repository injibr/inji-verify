package io.inji.verify.services.impl;

import io.inji.verify.dto.submission.VPTokenResultDto;
import io.inji.verify.exception.BankWebHookException;
import io.inji.verify.services.BankWebhookService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.util.Map;

/**
 * Implementation of the BankWebhookService interface.
 * This service is responsible for calling a predefined webhook URL.
 */
@Service
public class BankWebhookServiceImpl implements BankWebhookService {

    private final WebClient webClient;

    public BankWebhookServiceImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Calls the predefined webhook URL and handles the response.
     * In case of an error, it throws a BankWebHookException.
     */
    public void callWebhook(Map<String,ByteArrayInputStream> pdfs, VPTokenResultDto result,String webhookUrl) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            int index = 1;
            body.add("result", result);

            pdfs.forEach((fileName, bais) -> {
                        byte[] bytes = bais.readAllBytes(); // consume safely into memory

                        String filename = fileName+ ".pdf";

                        ByteArrayResource resource = new ByteArrayResource(bytes) {
                            @Override
                            public String getFilename() {
                                return filename; // so webhook can display filename
                            }
                        };

                        body.add(fileName, resource);
                    }
                    );

            String response = webClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            System.out.println("Webhook response: " + response);
        }catch (Exception ex){
            throw new BankWebHookException();
        }
    }
}
