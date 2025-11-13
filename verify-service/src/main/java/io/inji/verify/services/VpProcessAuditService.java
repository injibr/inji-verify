package io.inji.verify.services;

import io.inji.verify.models.VpProcessAudit;

import java.util.List;

public interface VpProcessAuditService {

    void logAudit(boolean isVcShared, String transactionId, String vcs);

    List<VpProcessAudit> getAuditsByTransaction(String transactionId);

    List<VpProcessAudit> getRecentAudits();

    VpProcessAudit updateAudit(VpProcessAudit audit);
}