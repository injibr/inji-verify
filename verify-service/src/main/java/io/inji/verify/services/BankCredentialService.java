package io.inji.verify.services;

import io.inji.verify.models.BankCredential;

public interface BankCredentialService {
    BankCredential findByBankId(String bankId);
}
