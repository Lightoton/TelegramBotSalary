package com.payroll.bot.repository;

import com.payroll.bot.entity.MonthlyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {
    Optional<MonthlyReport> findByChatIdAndReportMonthAndReportYear(Long chatId, Integer month, Integer year);
}
