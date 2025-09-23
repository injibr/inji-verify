package io.inji.verify.repository;

import io.inji.verify.models.VpRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VpRequestRepository extends JpaRepository<VpRequest, UUID> {
    VpRequest findByBankCredential_BankId(String bankId);
    VpRequest findByRequestId(String requestId);
}
