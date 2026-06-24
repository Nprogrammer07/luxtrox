package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.CashbackTransaction;
import com.luxtrox.backend.entity.InvestmentPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CashbackTransactionRepository extends JpaRepository<CashbackTransaction, UUID> {
    List<CashbackTransaction> findByPosition(InvestmentPosition position);
}
