package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.BankAccountType;
import jakarta.persistence.*;
import java.util.UUID;

/**
 * Hija 1:1 de WithdrawalRequest, solo cuando type = BANK (ver
 * docs/domain-model.md 2.10).
 */
@Entity
@Table(name = "bank_withdrawal_details")
public class BankWithdrawalDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "withdrawal_request_id", nullable = false, unique = true)
    private WithdrawalRequest withdrawalRequest;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "country", nullable = false, length = 80)
    private String country;

    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private BankAccountType accountType;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "account_holder_name", nullable = false, length = 150)
    private String accountHolderName;

    @Column(name = "document_id", nullable = false, length = 50)
    private String documentId;

    protected BankWithdrawalDetail() {
        // JPA
    }

    public BankWithdrawalDetail(WithdrawalRequest withdrawalRequest, String fullName, String email, String phone,
                                 String country, String bankName, BankAccountType accountType,
                                 String accountNumber, String accountHolderName, String documentId) {
        this.withdrawalRequest = withdrawalRequest;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.country = country;
        this.bankName = bankName;
        this.accountType = accountType;
        this.accountNumber = accountNumber;
        this.accountHolderName = accountHolderName;
        this.documentId = documentId;
    }

    public UUID getId() {
        return id;
    }

    public WithdrawalRequest getWithdrawalRequest() {
        return withdrawalRequest;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getCountry() {
        return country;
    }

    public String getBankName() {
        return bankName;
    }

    public BankAccountType getAccountType() {
        return accountType;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public String getDocumentId() {
        return documentId;
    }
}
