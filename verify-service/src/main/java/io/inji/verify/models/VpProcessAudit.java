package io.inji.verify.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
@Entity
@NoArgsConstructor
@Table(name = "vp_process_audit")
public class VpProcessAudit {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "is_vc_shared", nullable = false)
    private boolean isVcShared;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "vcs", columnDefinition = "TEXT")
    private String vcs;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "issued_date")
    private LocalDateTime issuedDate;
}
