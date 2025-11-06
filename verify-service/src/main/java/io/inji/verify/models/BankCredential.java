package io.inji.verify.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "bank_credentials")
public class BankCredential {

    @Id
    @Column(name = "bank_id", length = 100, nullable = false)
    private String bankId;

    @Column(name = "bank_name", nullable = false, length = 255)
    private String bankName;

    @Column(name = "api_key", nullable = false, length = 255)
    private String apiKey;

    @Column(name = "bank_secret", nullable = false, length = 255)
    private String bankSecret;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "bankCredential", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VpRequest> vpRequests;

    @Column(name = "bank_webhook_url", length = 500)
    private String bankWebhookUrl;

    @Column(name = "bank_webhook_uri", length = 500)
    private String bankWebhookUri;

    @Column(name = "bank_webhook_token_url", length = 500)
    private String bank_webhook_token_url;

    @Column(name = "bank_webhook_token_uri", length = 500)
    private String bank_webhook_token_uri;

    public String getBankWebhookUri() {
        return bankWebhookUri;
    }

    public void setBankWebhookUri(String bankWebhookUri) {
        this.bankWebhookUri = bankWebhookUri;
    }

    public String getBank_webhook_token_url() {
        return bank_webhook_token_url;
    }

    public void setBank_webhook_token_url(String bank_webhook_token_url) {
        this.bank_webhook_token_url = bank_webhook_token_url;
    }

    public String getBank_webhook_token_uri() {
        return bank_webhook_token_uri;
    }

    public void setBank_webhook_token_uri(String bank_webhook_token_uri) {
        this.bank_webhook_token_uri = bank_webhook_token_uri;
    }


    // Getters and Setters
    public String getBankId() {
        return bankId;
    }

    public void setBankId(String bankId) {
        this.bankId = bankId;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBankSecret() {
        return bankSecret;
    }

    public void setBankSecret(String bankSecret) {
        this.bankSecret = bankSecret;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<VpRequest> getVpRequests() {
        return vpRequests;
    }

    public void setVpRequests(List<VpRequest> vpRequests) {
        this.vpRequests = vpRequests;
    }

    public String getBankWebhookUrl() {
        return bankWebhookUrl;
    }

    public void setBankWebhookUrl(String bankWebhookUrl) {
        this.bankWebhookUrl = bankWebhookUrl;
    }

}
