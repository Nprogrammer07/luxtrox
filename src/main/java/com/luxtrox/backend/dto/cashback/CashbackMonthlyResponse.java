package com.luxtrox.backend.dto.cashback;

import java.math.BigDecimal;

/**
 * generated y received son el MISMO valor calculado -- ver la nota en
 * CashbackSummaryResponse sobre por que ese par de campos no
 * representan dos estados distintos en este backend.
 */
public record CashbackMonthlyResponse(
        String month,
        BigDecimal generated,
        BigDecimal received
) {
}
