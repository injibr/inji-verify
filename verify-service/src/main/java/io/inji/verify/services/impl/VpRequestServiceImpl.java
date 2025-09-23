package io.inji.verify.services.impl;

import io.inji.verify.models.BankCredential;
import io.inji.verify.models.VpRequest;
import io.inji.verify.repository.VpRequestRepository;
import io.inji.verify.services.BankCredentialService;
import io.inji.verify.services.VpRequestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class VpRequestServiceImpl implements VpRequestService {
    private final BankCredentialService bankCredentialService;
    private final VpRequestRepository vpRequestRepository;


    public VpRequestServiceImpl(BankCredentialService bankCredentialService, VpRequestRepository vpRequestRepository) {
        this.bankCredentialService = bankCredentialService;
        this.vpRequestRepository = vpRequestRepository;
    }

    @Override
    @Transactional
    public void saveVpRequest(String bankId, String requestId, String transactionId, String cpf_number) {
        // Fetch the BankCredential by bankId
        BankCredential bankCredential = bankCredentialService.findByBankId(bankId);
        if (Objects.isNull(bankCredential)) {
            throw new IllegalArgumentException("BankCredential not found for bankId: " + bankId);
        }

        // Create and save VpRequest
        VpRequest vpRequest = new VpRequest();
        vpRequest.setBankCredential(bankCredential);
        vpRequest.setRequestId(requestId);
        vpRequest.setTransactionId(transactionId);
        vpRequest.setCpfNumber(cpf_number);

        vpRequestRepository.save(vpRequest);
    }

    @Override
    public VpRequest getVpRequestsByRequestId(String requestId){
        return vpRequestRepository.findByRequestId(requestId);
    }
}
