package io.inji.verify.repository;

import io.inji.verify.models.BankCredential;
import org.springframework.data.jpa.repository.JpaRepository;


public interface BankCredentialRepository extends JpaRepository<BankCredential, String> {
    BankCredential findByBankId(String bankId);
}