package io.inji.verify.services.impl;

import io.inji.verify.models.VpProcessAudit;
import io.inji.verify.repository.VpProcessAuditRepository;
import io.inji.verify.services.VpProcessAuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class VpProcessAuditServiceImpl implements VpProcessAuditService {

    private final VpProcessAuditRepository repository;

    public VpProcessAuditServiceImpl(VpProcessAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public void logAudit(boolean isVcShared, String transactionId, String vcs) {
        VpProcessAudit audit = new VpProcessAudit();
        audit.setVcShared(isVcShared);
        audit.setTransactionId(transactionId);
        audit.setVcs(vcs);
        audit.setCreatedDate(LocalDateTime.now());

        if (isVcShared) {
            audit.setIssuedDate(LocalDateTime.now());
        }

        repository.save(audit);
    }

    @Override
    public List<VpProcessAudit> getAuditsByTransaction(String transactionId) {
        return repository.findByTransactionId(transactionId);
    }

    @Override
    public List<VpProcessAudit> getRecentAudits() {
        return repository.findAllByOrderByCreatedDateDesc();
    }

    @Override
    @Transactional
    public VpProcessAudit updateAudit(VpProcessAudit audit) {
        return repository.save(audit);  // performs UPDATE because id is present
    }
}