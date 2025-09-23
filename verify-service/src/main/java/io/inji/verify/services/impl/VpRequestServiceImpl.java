package io.inji.verify.services.impl;

import io.inji.verify.models.BankCredential;
import io.inji.verify.models.VpRequest;
import io.inji.verify.repository.VpRequestRepository;
import io.inji.verify.services.BankCredentialService;
import io.inji.verify.services.VpRequestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public void saveVpRequest(String bankId, String bankSecret, String requestId, String transactionId) {
        // Fetch the BankCredential by bankId
        BankCredential bankCredential = validateAndGetBank(bankId,bankSecret);

        // Create and save VpRequest
        VpRequest vpRequest = new VpRequest();
        vpRequest.setBankCredential(bankCredential);
        vpRequest.setRequestId(requestId);
        vpRequest.setTransactionId(transactionId);

        vpRequestRepository.save(vpRequest);
    }

    @Override
    public VpRequest getVpRequestsByRequestId(String requestId){
        return vpRequestRepository.findByRequestId(requestId);
    }

    private BankCredential validateAndGetBank(String bankId,String bankSecret){
        return bankCredentialService.checkIfBankSecretMatches(bankId, bankSecret);
    }
}
