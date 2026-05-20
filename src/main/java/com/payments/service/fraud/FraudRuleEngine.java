package com.payments.service.fraud;

import com.payments.domain.entity.Card;
import com.payments.domain.enums.ResponseCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FraudRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(FraudRuleEngine.class);

    private final VelocityCheckRule velocityCheckRule;
    private final AmountThresholdRule amountThresholdRule;
    private final GeoMismatchRule geoMismatchRule;
    private final ObjectMapper objectMapper;

    private static final int FRAUD_SCORE_DECLINE_THRESHOLD = 70;

    public FraudRuleEngine(VelocityCheckRule velocityCheckRule,
                           AmountThresholdRule amountThresholdRule,
                           GeoMismatchRule geoMismatchRule,
                           ObjectMapper objectMapper) {
        this.velocityCheckRule = velocityCheckRule;
        this.amountThresholdRule = amountThresholdRule;
        this.geoMismatchRule = geoMismatchRule;
        this.objectMapper = objectMapper;
    }

    public FraudResult evaluate(Card card, long amount, String ipAddress) {
        List<FraudRule> rules = List.of(velocityCheckRule, amountThresholdRule, geoMismatchRule);
        List<String> triggeredFlags = new ArrayList<>();
        int totalScore = 0;

        for (FraudRule rule : rules) {
            FraudRule.RuleResult result = rule.evaluate(card, amount, ipAddress);
            if (result.triggered()) {
                triggeredFlags.add(result.ruleCode() + ": " + result.reason());
                totalScore += result.scoreContribution();
                log.debug("Fraud rule triggered: {} (score +{})", result.ruleCode(), result.scoreContribution());
                if (result.hardDecline()) {
                    log.warn("Hard decline from rule: {}", result.ruleCode());
                    return new FraudResult(totalScore, toJson(triggeredFlags), true, result.responseCode());
                }
            }
        }

        if (totalScore >= FRAUD_SCORE_DECLINE_THRESHOLD) {
            log.warn("Fraud score {} exceeds threshold, declining", totalScore);
            return new FraudResult(totalScore, toJson(triggeredFlags), true, ResponseCode.FRAUD_SUSPECTED);
        }

        return new FraudResult(totalScore, toJson(triggeredFlags), false, ResponseCode.APPROVED);
    }

    private String toJson(List<String> flags) {
        try { return objectMapper.writeValueAsString(flags); }
        catch (JsonProcessingException e) { return "[]"; }
    }

    public record FraudResult(int score, String flagsJson, boolean shouldDecline, ResponseCode responseCode) {}
}
