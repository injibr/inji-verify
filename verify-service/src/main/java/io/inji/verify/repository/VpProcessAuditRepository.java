package io.inji.verify.repository;

import io.inji.verify.models.VpProcessAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VpProcessAuditRepository extends JpaRepository<VpProcessAudit, UUID> {

    // fetch all audits for a transaction
    List<VpProcessAudit> findByTransactionId(String transactionId);

    // get only the records where VC is shared
    List<VpProcessAudit> findByIsVcShared(boolean isVcShared);

    // get audits in latest-first order
    List<VpProcessAudit> findAllByOrderByCreatedDateDesc();
}
