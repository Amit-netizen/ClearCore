package com.payments.service.fraud;

import com.payments.domain.entity.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GeoMismatchRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(GeoMismatchRule.class);

    @Override
    public RuleResult evaluate(Card card, long amount, String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) return RuleResult.pass("GEO_MISMATCH");
        if (isPrivateIp(ipAddress)) return RuleResult.pass("GEO_MISMATCH");
        log.debug("GeoMismatch stub passed for IP: {}", ipAddress);
        return RuleResult.pass("GEO_MISMATCH");
    }

    private boolean isPrivateIp(String ip) {
        return ip.startsWith("10.") || ip.startsWith("172.16.")
                || ip.startsWith("192.168.") || ip.startsWith("127.")
                || ip.equals("0:0:0:0:0:0:0:1");
    }
}
