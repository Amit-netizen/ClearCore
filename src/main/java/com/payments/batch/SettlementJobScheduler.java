package com.payments.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SettlementJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(SettlementJobScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    public SettlementJobScheduler(JobLauncher jobLauncher, Job settlementJob) {
        this.jobLauncher = jobLauncher;
        this.settlementJob = settlementJob;
    }

    @Scheduled(cron = "${payments.settlement.cron:0 0 * * * *}")
    public void runSettlementJob() {
        log.info("Triggering settlement batch job");
        try {
            var params = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();
            var execution = jobLauncher.run(settlementJob, params);
            log.info("Settlement job launched: executionId={}, status={}", execution.getId(), execution.getStatus());
        } catch (Exception e) {
            log.error("Failed to launch settlement job", e);
        }
    }
}
