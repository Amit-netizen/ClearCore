package com.payments.batch;

import com.payments.domain.entity.Merchant;
import com.payments.domain.entity.SettlementRecord;
import com.payments.domain.entity.Transaction;
import com.payments.domain.enums.TransactionStatus;
import com.payments.repository.MerchantRepository;
import com.payments.repository.SettlementRecordRepository;
import com.payments.repository.TransactionRepository;
import com.payments.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
public class SettlementBatchConfig {

    private static final Logger log = LoggerFactory.getLogger(SettlementBatchConfig.class);
    private static final double SETTLEMENT_FEE_RATE = 0.02;
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final SettlementRecordRepository settlementRecordRepository;
    private final LedgerService ledgerService;

    public SettlementBatchConfig(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  TransactionRepository transactionRepository,
                                  MerchantRepository merchantRepository,
                                  SettlementRecordRepository settlementRecordRepository,
                                  LedgerService ledgerService) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.settlementRecordRepository = settlementRecordRepository;
        this.ledgerService = ledgerService;
    }

    @Bean
    public Job settlementJob(Step settlementStep) {
        return new JobBuilder("settlementJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(settlementStep)
                .listener(settlementJobListener())
                .build();
    }

    @Bean
    public Step settlementStep() {
        return new StepBuilder("settlementStep", jobRepository)
                .<MerchantSettlementData, SettlementRecord>chunk(CHUNK_SIZE, transactionManager)
                .reader(settlementReader())
                .processor(settlementProcessor())
                .writer(settlementWriter())
                .faultTolerant().retryLimit(3).retry(Exception.class)
                .build();
    }

    @Bean
    @StepScope
    public ListItemReader<MerchantSettlementData> settlementReader() {
        List<Transaction> allCaptured = transactionRepository.findByStatus(TransactionStatus.CAPTURED);
        Map<UUID, List<Transaction>> byMerchant = allCaptured.stream()
                .collect(Collectors.groupingBy(t -> t.getMerchant().getId()));
        List<MerchantSettlementData> settlements = byMerchant.entrySet().stream()
                .map(e -> new MerchantSettlementData(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        log.info("Settlement batch: {} merchants, {} transactions", settlements.size(), allCaptured.size());
        return new ListItemReader<>(settlements);
    }

    @Bean
    public ItemProcessor<MerchantSettlementData, SettlementRecord> settlementProcessor() {
        return data -> {
            long totalAmount = data.transactions().stream().mapToLong(Transaction::getCapturedAmount).sum();
            long feeAmount = (long) (totalAmount * SETTLEMENT_FEE_RATE);
            long netAmount = totalAmount - feeAmount;
            Merchant merchant = merchantRepository.findById(data.merchantId())
                    .orElseThrow(() -> new IllegalStateException("Merchant not found: " + data.merchantId()));
            for (Transaction txn : data.transactions()) {
                ledgerService.recordSettlement(txn);
                txn.setStatus(TransactionStatus.SETTLED);
                transactionRepository.save(txn);
            }
            merchant.creditBalance(netAmount);
            merchantRepository.save(merchant);
            return SettlementRecord.builder()
                    .merchant(merchant).settlementDate(LocalDate.now())
                    .totalTransactions(data.transactions().size())
                    .totalAmount(totalAmount).feeAmount(feeAmount).netAmount(netAmount)
                    .status("COMPLETED").processedAt(LocalDateTime.now()).build();
        };
    }

    @Bean
    public ItemWriter<SettlementRecord> settlementWriter() {
        return records -> {
            settlementRecordRepository.saveAll(records.getItems());
            log.info("Wrote {} settlement records", records.size());
        };
    }

    @Bean
    public JobExecutionListener settlementJobListener() {
        return new JobExecutionListener() {
            @Override public void beforeJob(JobExecution e) { log.info("Settlement job starting"); }
            @Override public void afterJob(JobExecution e) { log.info("Settlement job finished: {}", e.getStatus()); }
        };
    }

    public record MerchantSettlementData(UUID merchantId, List<Transaction> transactions) {}
}
