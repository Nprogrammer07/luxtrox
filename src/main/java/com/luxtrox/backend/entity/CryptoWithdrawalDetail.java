package com.luxtrox.backend.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Hija 1:1 de WithdrawalRequest, solo cuando type = CRYPTO. Tabla
 * separada (en vez de columnas nullable en una sola tabla) por
 * trazabilidad y seguridad de los datos -- decision explicita del
 * cliente (ver docs/domain-model.md 2.9).
 */
@Entity
@Table(name = "crypto_withdrawal_details")
public class CryptoWithdrawalDetail {

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

    @Column(name = "blockchain_network", nullable = false, length = 30)
    private String blockchainNetwork;

    @Column(name = "wallet_address", nullable = false)
    private String walletAddress;

    @Column(name = "transaction_hash")
    private String transactionHash;

    protected CryptoWithdrawalDetail() {
        // JPA
    }

    public CryptoWithdrawalDetail(WithdrawalRequest withdrawalRequest, String fullName, String email,
                                   String phone, String blockchainNetwork, String walletAddress) {
        this.withdrawalRequest = withdrawalRequest;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.blockchainNetwork = blockchainNetwork;
        this.walletAddress = walletAddress;
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

    public String getBlockchainNetwork() {
        return blockchainNetwork;
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }
}
