package io.inji.verify.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.inji.verify.dto.submission.VPTokenResultDto;
import io.inji.verify.exception.BankWebHookException;
import io.inji.verify.services.BankWebhookService;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of the BankWebhookService interface.
 * This service is responsible for calling a predefined webhook URL.
 */
@Service
@Slf4j
public class BankOfBrazilWebhookServiceImpl implements BankWebhookService {
    @Qualifier("webClientWithPemCert")
    private final String webhookTokenUrl;
    private final String webhookTokenUri;
    private final String auth;
    private final String grantType;
    private final String scope;
    private final String clientCertPath;
    private final String clientKeyPath;
    private final String caCertPath;
    private final String bbApiKey;

    public BankOfBrazilWebhookServiceImpl(
            @Value("${govbr.bb.token.base.url}") String webhookTokenUrl,
            @Value("${govbr.bb.token.uri}") String webhookTokenUri,
            @Value("${govbr.bb.token.auth}") String auth,
            @Value("${govbr.bb.token.grant.type}") String grantType,
            @Value("${govbr.bb.token.scope}") String scope,
            @Value("${govbr.bb.client.cert-path}") String clientCertPath,
            @Value("${govbr.bb.client.key-path}") String clientKeyPath,
            @Value("${govbr.bb.ca.cert-path}") String caCertPath,
            @Value("${govbr.bb.api.key}") String bbApiKey) {
        this.webhookTokenUrl = webhookTokenUrl;
        this.webhookTokenUri = webhookTokenUri;
        this.auth = auth;
        this.grantType = grantType;
        this.scope = scope;
        this.clientCertPath = clientCertPath;
        this.clientKeyPath = clientKeyPath;
        this.caCertPath = caCertPath;
        this.bbApiKey = bbApiKey;
    }

    /**
     * Calls the predefined webhook URL and handles the response.
     * In case of an error, it throws a BankWebHookException.
     */
    public void callWebhook(Map<String, ByteArrayInputStream> pdfs, VPTokenResultDto result, String webhookUrl, String apiKey) {
        try {
            log.info("Preparing to call Bank of Brazil webhook");
            Resource clientCertResource = new ClassPathResource(clientCertPath);
            Resource clientKeyResource  = new ClassPathResource(clientKeyPath);
            Resource caCertResource     = new ClassPathResource(caCertPath);

            InputStream clientCertStream = clientCertResource.getInputStream();
            InputStream clientKeyStream  = clientKeyResource.getInputStream();
            InputStream caCertStream     = caCertResource.getInputStream();

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null); // load default system CAs

            // Build Netty SslContext for the client
            SslContext sslContext = SslContextBuilder.forClient()
                    .keyManager(clientCertStream, clientKeyStream)
                    .trustManager(tmf)
                    .build();

            HttpClient httpClient = HttpClient.create()
                    .secure(spec -> spec.sslContext(sslContext));

            WebClient webClient1 = WebClient.builder()
                    .baseUrl(webhookUrl)
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();


            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("result", result);

            pdfs.forEach((fileName, bais) -> {
                        byte[] bytes = bais.readAllBytes();

                        String filename = fileName + ".pdf";

                        ByteArrayResource resource = new ByteArrayResource(bytes) {
                            @Override
                            public String getFilename() {
                                return filename;
                            }
                        };

                        body.add(fileName, resource);
                    }
            );


            String bearerToken = "Bearer " + getAccessToken();
            log.info("Bearer Token is provided for webhook call");
            webClient1.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/response/files")
                            .queryParam(bbApiKey, apiKey)
                            .build())
                    .header("Authorization", bearerToken)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .exchangeToMono(clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .map(bodyStr -> {
                                        HttpStatusCode status = clientResponse.statusCode();
                                        log.error("STATUS: {}", status.value());
                                        log.error("HEADERS: {}", clientResponse.headers().asHttpHeaders());
                                        log.error("BODY: {}", bodyStr);

                                        Map<String, Object> map = new HashMap<>();
                                        map.put("status", status.value());
                                        map.put("body", bodyStr);
                                        return map;
                                    })
                    )
                    .block();
        } catch (Exception ex) {
            log.error("Error while calling bank webhook", ex);
            throw new BankWebHookException();
        }
    }

    /**
     * Retrieves an access token from the webhook token URL.
     *
     * @return the access token as a String
     */
    private String getAccessToken() {
        WebClient webClient = WebClient.builder()
                .baseUrl(webhookTokenUrl)
                .defaultHeader("Authorization", auth)
                .defaultHeader("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();

        Mono<JsonNode> responseMono = webClient.post()
                .uri(webhookTokenUri)
                .body(BodyInserters.fromFormData("grant_type", grantType)
                        .with("scope", scope))
                .retrieve()
                .bodyToMono(JsonNode.class);

        JsonNode response = responseMono.block();

        if (response != null && response.has("access_token")) {
            return response.get("access_token").asText();
        } else {
            throw new RuntimeException("Failed to retrieve access token for webhook");
        }
    }
}
