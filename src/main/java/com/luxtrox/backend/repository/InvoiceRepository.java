package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.Invoice;
import com.luxtrox.backend.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByPurchase(Purchase purchase);
}
