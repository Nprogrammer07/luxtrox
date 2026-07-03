package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.PlusLicense;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.PlusLicenseStatus;
import com.luxtrox.backend.repository.PlusLicenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlusService {

    private final PlusLicenseRepository plusLicenseRepository;

    public PlusService(PlusLicenseRepository plusLicenseRepository) {
        this.plusLicenseRepository = plusLicenseRepository;
    }

    /** Crea la licencia Plus cuando una compra se confirma. */
    @Transactional
    public PlusLicense createLicense(User user, Purchase purchase) {
        PlusLicense license = new PlusLicense(user, purchase);
        return plusLicenseRepository.save(license);
    }

    /** Devuelve todas las licencias Plus del usuario, ordenadas por mas reciente. */
    @Transactional(readOnly = true)
    public List<PlusLicense> getLicensesForUser(User user) {
        return plusLicenseRepository.findByUserOrderByPurchasedAtDesc(user);
    }

    /**
     * Verifica si el usuario tiene una licencia Plus ACTIVA.
     * Se usa para aplicar el descuento de $100 al comprar Zenith.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveLicense(User user) {
        return plusLicenseRepository.existsByUserAndStatus(user, PlusLicenseStatus.ACTIVE);
    }
}
