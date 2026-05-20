package com.payments.service.fraud;

import com.payments.domain.entity.Card;
import com.payments.domain.enums.ResponseCode;

/**
 * Chain-of-responsibility pattern for fraud detection.
 * Each rule is independent and testable in isolation.
 * New rules can be added without modifying existing ones.
 */
public interface FraudRule {

    /**
     * Evaluate this fraud rule against the transaction.
     *
     * @param card      the card being charged
     * @param amount    transaction amount in paise
     * @param ipAddress request IP address
     * @return RuleResult with pass/fail, score contribution, and reason
     */
    RuleResult evaluate(Card card, long amount, String ipAddress);

    /**
     * Result of a single fraud rule evaluation.
     */
    record RuleResult(
            boolean triggered,
            int scoreContribution,
            String ruleCode,
            String reason,
            ResponseCode responseCode,
            boolean hardDecline  // if true, skip remaining rules and decline immediately
    ) {
        public static RuleResult pass(String ruleCode) {
            return new RuleResult(false, 0, ruleCode, null, ResponseCode.APPROVED, false);
        }

        public static RuleResult flag(String ruleCode, String reason, int score) {
            return new RuleResult(true, score, ruleCode, reason, ResponseCode.FRAUD_SUSPECTED, false);
        }

        public static RuleResult decline(String ruleCode, String reason, ResponseCode code) {
            return new RuleResult(true, 100, ruleCode, reason, code, true);
        }
    }
}
