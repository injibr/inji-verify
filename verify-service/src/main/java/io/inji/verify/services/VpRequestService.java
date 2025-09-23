package io.inji.verify.services;

import io.inji.verify.models.VpRequest;

public interface VpRequestService {
    void saveVpRequest(String bankId, String requestId, String transactionId);
    VpRequest getVpRequestsByRequestId(String requestId);
}
