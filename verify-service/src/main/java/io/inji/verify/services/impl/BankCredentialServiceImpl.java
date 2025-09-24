package io.inji.verify.services.impl;

import io.inji.verify.exception.BankCredentialException;
import io.inji.verify.models.BankCredential;
import io.inji.verify.repository.BankCredentialRepository;
import io.inji.verify.services.BankCredentialService;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class BankCredentialServiceImpl implements BankCredentialService {
    private final BankCredentialRepository bankCredentialRepository;

    public BankCredentialServiceImpl(BankCredentialRepository bankCredentialRepository) {
        this.bankCredentialRepository = bankCredentialRepository;
    }

    @Override
    public BankCredential findByBankId(String bankId) {
        return bankCredentialRepository.findByBankId(bankId);
    }

    @Override
    public BankCredential checkIfBankSecretMatches(String bankId, String secret){
        BankCredential bankCredential = bankCredentialRepository.findByBankId(bankId);
        if(Objects.isNull(bankCredential) || !bankCredential.getBankSecret().equals(secret)) throw new BankCredentialException();
        return bankCredential;
    }
}
