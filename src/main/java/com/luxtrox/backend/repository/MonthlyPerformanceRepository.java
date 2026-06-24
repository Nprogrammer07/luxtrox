package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.MonthlyPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyPerformanceRepository extends JpaRepository<MonthlyPerformance, UUID> {
    Optional<MonthlyPerformance> findByMonthAndYear(Integer month, Integer year);
}
