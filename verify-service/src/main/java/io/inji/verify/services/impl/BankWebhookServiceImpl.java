package io.inji.verify.services.impl;

import io.inji.verify.exception.BankWebHookException;
import io.inji.verify.services.BankWebhookService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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
    public void callWebhook() {
        try {
            String webhookUrl = "https://webhook.site/785f9f20-ba6d-45f3-bb7d-c2fd64f85f59";
            webClient.post()
                    .uri(webhookUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnNext(response -> System.out.println("Webhook response: " + response))
                    .doOnError(error -> System.err.println("Webhook call failed: " + error.getMessage()))
                    .block(); // blocking for simplicity, can be async if needed
        }catch (Exception ex){
            throw new BankWebHookException();
        }
    }
}
