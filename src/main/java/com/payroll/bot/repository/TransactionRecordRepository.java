package com.payroll.bot.repository;

import com.payroll.bot.entity.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {
    List<TransactionRecord> findAllByChatIdAndTransactionDate(Long chatId, LocalDate date);
    
    List<TransactionRecord> findAllByChatId(Long chatId);
    
    List<TransactionRecord> findByChatIdOrderByTransactionDateDesc(Long chatId);

    @Query("SELECT SUM(t.earnedAmount) FROM TransactionRecord t WHERE t.chatId = :chatId AND EXTRACT(MONTH FROM t.transactionDate) = :month AND EXTRACT(YEAR FROM t.transactionDate) = :year")
    Optional<java.math.BigDecimal> sumEarnedAmountByChatIdAndMonthAndYear(@Param("chatId") Long chatId, @Param("month") int month, @Param("year") int year);
}
