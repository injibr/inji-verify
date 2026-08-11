package io.inji.verify.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.inji.verify.dto.submission.VPTokenResultDto;
import io.inji.verify.exception.BankWebHookException;
import io.inji.verify.services.BankWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.URI;
import java.util.Map;

/**
 * Implementation of the BankWebhookService interface.
 * This service is responsible for calling a predefined webhook URL.
 */
@Service
@Slf4j
public class BankOfBrazilWebhookServiceImpl implements BankWebhookService {
    @Qualifier("webClientWithPemCert")
    private final String auth;
    private final String grantType;
    private final String scope;
    private final String keyStorePath;
    private final String keyStorePass;
    private final String truststorePath;
    private final String truststorePass;

    private final String bbApiKey;

    public BankOfBrazilWebhookServiceImpl(
            @Value("${govbr.bb.token.auth}") String auth,
            @Value("${govbr.bb.token.grant.type}") String grantType,
            @Value("${govbr.bb.token.scope}") String scope,
            @Value("${mtls.client.keystore-path}") String keyStorePath,
            @Value("${mtls.client.keystore-password}") String keyStorePass,
            @Value("${mtls.client.truststore-path}") String truststorePath,
            @Value("${mtls.client.truststore-password}") String truststorePass,
            @Value("${govbr.bb.api.key}") String bbApiKey) {
        this.auth = auth;
        this.grantType = grantType;
        this.scope = scope;
        this.keyStorePath = keyStorePath;
        this.keyStorePass = keyStorePass;
        this.truststorePath = truststorePath;
        this.truststorePass = truststorePass;
        this.bbApiKey = bbApiKey;
    }

    /**
     * Calls the predefined webhook URL and handles the response.
     * In case of an error, it throws a BankWebHookException.
     */
    public void callWebhook(Map<String, ByteArrayInputStream> pdfs,
                            VPTokenResultDto result,
                            String webhookUrl,
                            String apiKey,
                            String tokenUrl,
                            String tokenUri,
                            String webhookUri){
        try {
            log.info("Preparing to call Bank of Brazil webhook");
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

                        body.add(fileName + ".pdf", resource);
                    }
            );

            String bearerToken = "Bearer " + getAccessToken(tokenUrl,tokenUri);
            log.info("Token retrieved successfully");

            SSLContext sslContext = SSLContexts.custom()
                    .loadKeyMaterial(
                            new File(keyStorePath),
                            keyStorePass.toCharArray(),
                            keyStorePass.toCharArray()
                    )
                    .loadTrustMaterial(
                            new File(truststorePath),
                            truststorePass.toCharArray()
                    )
                    .build();

            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
            HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(cm)
                    .build()) {

                URI uri = new URIBuilder(webhookUrl + webhookUri)
                        .addParameter(bbApiKey, apiKey)
                        .build();
                HttpPost post = new HttpPost(uri);


                post.addHeader("Authorization", bearerToken);
                post.addHeader("Cookie", "__cf_bm=u4eLY.1sDyjTRdGFWIVnUrH4bFmEEB3P2.WtKpTsef4-1760078340-1.0.1.1-XQOSwg2UwuJpAg.pVSB0BY7d.nsR8WNdMOtsZEgN0pBpKxI.jVqaRF5ho7cV8uaSQBBQJOIEpNvzTdtS8ysUlNnaNiAIg_zJTkuzlVEpDDE; 95dcb4e7d7f128466148ace27fd72dba=87e16e8d4685c5875c91d9acb83b4d82");


                MultipartEntityBuilder builder = MultipartEntityBuilder.create();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode resultNode = mapper.valueToTree(result);
                if (resultNode.has("vcResults")) {
                    for (JsonNode vcResult : resultNode.get("vcResults")) {
                        if (vcResult.has("vc") && vcResult.get("vc").isTextual()) {
                            try {
                                JsonNode parsedVc = mapper.readTree(vcResult.get("vc").asText());
                                ((com.fasterxml.jackson.databind.node.ObjectNode) vcResult).set("vc", parsedVc);
                            } catch (Exception e) {
                                log.warn("Failed to parse vc field as JSON, sending as escaped string: {}", e.getMessage());
                            }
                        }
                    }
                }
                String resultsJson = mapper.writeValueAsString(resultNode);

                builder.addPart("results",
                        new StringBody(resultsJson, ContentType.APPLICATION_JSON));

                pdfs.forEach((fileName, bais) -> {
                    try {
                        if (bais.markSupported()) {
                            bais.reset();
                        }

                        File tempFile = File.createTempFile(fileName, ".pdf");

                        try (OutputStream out = new FileOutputStream(tempFile)) {
                            bais.transferTo(out);
                        }

                        builder.addPart(
                                fileName + ".pdf",
                                new FileBody(tempFile, ContentType.APPLICATION_OCTET_STREAM, tempFile.getName())
                        );

                    } catch (IOException e) {
                        throw new RuntimeException("Error creating temp file for " + fileName, e);
                    }
                });

                post.setEntity(builder.build());

                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    if (response.getCode() == 200) {
                        log.info("Response Body: {}", EntityUtils.toString(response.getEntity()));
                        log.info("Successfully called Bank of Brazil webhook");
                    } else {
                        log.error("Failed to call Bank of Brazil webhook. Status Code: {}", response.getCode());
                        log.error("Response Body: {}", EntityUtils.toString(response.getEntity()));
                        throw new BankWebHookException();
                    }

                }
            }
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
    private String getAccessToken(String tokenUrl,
                                  String tokenUri) {
        log.info("calling webhook token URL to get token");
        WebClient webClient = WebClient.builder()
                .baseUrl(tokenUrl)
                .defaultHeader("Authorization", auth)
                .defaultHeader("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build();

        Mono<JsonNode> responseMono = webClient.post()
                .uri(tokenUri)
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
