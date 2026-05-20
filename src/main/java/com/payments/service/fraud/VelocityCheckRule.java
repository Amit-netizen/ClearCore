package com.payments.service.fraud;

import com.payments.domain.entity.Card;
import com.payments.domain.enums.ResponseCode;
import com.payments.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class VelocityCheckRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(VelocityCheckRule.class);

    private final TransactionRepository transactionRepository;

    @Value("${payments.fraud.velocity-limit-per-minute:5}")
    private int velocityLimitPerMinute;

    public VelocityCheckRule(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public RuleResult evaluate(Card card, long amount, String ipAddress) {
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        long recentTxnCount = transactionRepository
                .countByCardIdAndCreatedAtAfter(card.getId(), oneMinuteAgo);

        if (recentTxnCount >= velocityLimitPerMinute) {
            log.warn("Velocity check triggered for card {}: {} txns in last minute",
                    card.getId(), recentTxnCount);
            return RuleResult.decline("VELOCITY_CHECK",
                    "Transaction velocity exceeded: " + recentTxnCount + " txns/min",
                    ResponseCode.RESTRICTED_CARD);
        }
        return RuleResult.pass("VELOCITY_CHECK");
    }
}
