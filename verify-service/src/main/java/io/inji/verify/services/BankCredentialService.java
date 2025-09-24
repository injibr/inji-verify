package io.inji.verify.services;

import io.inji.verify.models.BankCredential;

public interface BankCredentialService {
    BankCredential findByBankId(String bankId);
    BankCredential checkIfBankSecretMatches(String bankId, String secret);
}
