package io.inji.verify.models;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.util.UUID;

@Entity
@Table(name = "vp_requests")
public class VpRequest {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "request_id", nullable = false, length = 255)
    private String requestId;

    @Column(name = "transaction_id", nullable = false, length = 255)
    private String transactionId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_credential_id", referencedColumnName = "bank_id", nullable = false)
    private BankCredential bankCredential;

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public BankCredential getBankCredential() {
        return bankCredential;
    }

    public void setBankCredential(BankCredential bankCredential) {
        this.bankCredential = bankCredential;
    }
}

