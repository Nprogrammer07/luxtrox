package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.SystemConfig;
import com.luxtrox.backend.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class SystemConfigService {

    public static final String KEY_MIN_WITHDRAWAL = "min_withdrawal";

    private final SystemConfigRepository repository;

    public SystemConfigService(SystemConfigRepository repository) {
        this.repository = repository;
    }

    public BigDecimal getMinWithdrawal() {
        return getBigDecimal(KEY_MIN_WITHDRAWAL, new BigDecimal("50.00"));
    }

    @Transactional
    public void update(String key, String value) {
        SystemConfig config = repository.findById(key)
                .orElse(new SystemConfig(key, value, null));
        config.setValue(value);
        repository.save(config);
    }

    private BigDecimal getBigDecimal(String key, BigDecimal fallback) {
        return repository.findById(key)
                .map(c -> new BigDecimal(c.getValue()))
                .orElse(fallback);
    }
}
