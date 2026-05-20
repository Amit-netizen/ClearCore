package com.payments.service.fraud;

import com.payments.domain.entity.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AmountThresholdRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(AmountThresholdRule.class);

    @Value("${payments.fraud.single-txn-amount-threshold:10000000}")
    private long thresholdInPaise;

    @Override
    public RuleResult evaluate(Card card, long amount, String ipAddress) {
        if (amount > thresholdInPaise) {
            log.warn("Large transaction flagged: cardId={}, amount={}", card.getId(), amount);
            return RuleResult.flag("AMOUNT_THRESHOLD",
                    "Transaction amount " + amount + " exceeds threshold " + thresholdInPaise, 30);
        }
        return RuleResult.pass("AMOUNT_THRESHOLD");
    }
}
