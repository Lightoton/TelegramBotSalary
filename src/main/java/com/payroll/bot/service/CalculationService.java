package com.payroll.bot.service;

import com.payroll.bot.entity.TransactionRecord;
import com.payroll.bot.entity.UserSettings;
import com.payroll.bot.repository.TransactionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalculationService {

    private final UserService userService;
    private final TransactionRecordRepository transactionRepository;
    private final com.payroll.bot.repository.MonthlyReportRepository monthlyReportRepository;

    @Transactional
    public TransactionRecord addIncome(Long chatId, LocalDate date, String serviceType, String inputValueStr) {
        UserSettings settings = userService.getUserSettings(chatId);
        BigDecimal earnedAmount = BigDecimal.ZERO;
        BigDecimal inputValue = BigDecimal.ZERO;

        if ("HOURLY".equals(serviceType)) {
            // Expected format: HH:mm-HH:mm (e.g. 13:00-15:30)
            inputValue = calculateHours(inputValueStr);
            earnedAmount = inputValue.multiply(settings.getHourlyRate()).setScale(2, RoundingMode.HALF_UP);
        } else {
            java.util.List<com.payroll.bot.entity.ServiceCategory> services = userService.getServiceCategories(chatId);
            com.payroll.bot.entity.ServiceCategory category = services.stream()
                .filter(s -> s.getName().equals(serviceType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown service type: " + serviceType));
                
            inputValue = new BigDecimal(inputValueStr.replace(",", "."));
            earnedAmount = inputValue.multiply(category.getPercentage()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        TransactionRecord record = new TransactionRecord(null, chatId, serviceType, inputValue, earnedAmount, date, LocalDateTime.now());
        return transactionRepository.save(record);
    }

    public TransactionRecord getTransactionById(Long id) {
        return transactionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
    }

    @Transactional
    public void updateTransaction(Long transactionId, String newInputValueStr) {
        TransactionRecord record = getTransactionById(transactionId);
        UserSettings settings = userService.getUserSettings(record.getChatId());
        
        BigDecimal earnedAmount = BigDecimal.ZERO;
        BigDecimal inputValue = BigDecimal.ZERO;

        if ("HOURLY".equals(record.getServiceType())) {
            inputValue = calculateHours(newInputValueStr);
            earnedAmount = inputValue.multiply(settings.getHourlyRate()).setScale(2, RoundingMode.HALF_UP);
        } else {
            java.util.List<com.payroll.bot.entity.ServiceCategory> services = userService.getServiceCategories(record.getChatId());
            com.payroll.bot.entity.ServiceCategory category = services.stream()
                .filter(s -> s.getName().equals(record.getServiceType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown service type: " + record.getServiceType()));
                
            inputValue = new BigDecimal(newInputValueStr.replace(",", "."));
            earnedAmount = inputValue.multiply(category.getPercentage()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        record.setInputValue(inputValue);
        record.setEarnedAmount(earnedAmount);
        transactionRepository.save(record);
    }

    private BigDecimal calculateHours(String timeInterval) {
        try {
            timeInterval = timeInterval.replace(" ", "");
            String[] parts = timeInterval.split("-");
            String[] start = parts[0].split(":");
            String[] end = parts[1].split(":");

            int startHour = Integer.parseInt(start[0]);
            int startMin = Integer.parseInt(start[1]);
            int endHour = Integer.parseInt(end[0]);
            int endMin = Integer.parseInt(end[1]);

            int totalMinutes = (endHour * 60 + endMin) - (startHour * 60 + startMin);
            if (totalMinutes < 0) {
                totalMinutes += 24 * 60;
            }
            return new BigDecimal(totalMinutes).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid time format. Please use HH:mm-HH:mm");
        }
    }
    
    public BigDecimal getMonthlyTotal(Long chatId, int month, int year) {
        return transactionRepository.sumEarnedAmountByChatIdAndMonthAndYear(chatId, month, year)
                .orElse(BigDecimal.ZERO);
    }
    
    public List<TransactionRecord> getDailyTransactions(Long chatId, LocalDate date) {
        return transactionRepository.findAllByChatIdAndTransactionDate(chatId, date);
    }

    public List<TransactionRecord> getTransactionsByMonth(Long chatId, int month, int year) {
        return transactionRepository.findAllByChatId(chatId).stream()
            .filter(t -> t.getTransactionDate().getMonthValue() == month 
                    && t.getTransactionDate().getYear() == year)
            .toList();
    }
    @Transactional
    public void closeMonth(Long chatId, int month, int year) {
        if (isMonthClosed(chatId, month, year)) {
            throw new IllegalStateException("Этот месяц уже закрыт!");
        }

        BigDecimal total = getMonthlyTotal(chatId, month, year);
        com.payroll.bot.entity.MonthlyReport report = new com.payroll.bot.entity.MonthlyReport(null, chatId, month, year, total, LocalDateTime.now());
        monthlyReportRepository.save(report);
    }

    public boolean isMonthClosed(Long chatId, int month, int year) {
        return monthlyReportRepository.findAll().stream()
            .anyMatch(r -> r.getChatId().equals(chatId) && r.getReportMonth() == month && r.getReportYear() == year);
    }

    public List<YearMonth> getUnclosedMonths(Long chatId) {
        Set<YearMonth> activeMonths = transactionRepository.findAllByChatId(chatId).stream()
            .map(t -> YearMonth.of(t.getTransactionDate().getYear(), t.getTransactionDate().getMonthValue()))
            .collect(Collectors.toSet());

        Set<YearMonth> closedMonths = getArchivedMonths(chatId).stream()
            .map(r -> YearMonth.of(r.getReportYear(), r.getReportMonth()))
            .collect(Collectors.toSet());

        activeMonths.removeAll(closedMonths);

        return activeMonths.stream()
            .sorted((a, b) -> {
                if (a.getYear() != b.getYear()) {
                    return Integer.compare(b.getYear(), a.getYear());
                }
                return Integer.compare(b.getMonthValue(), a.getMonthValue());
            })
            .toList();
    }

    public List<com.payroll.bot.entity.MonthlyReport> getArchivedMonths(Long chatId) {
        return monthlyReportRepository.findAll().stream()
            .filter(r -> r.getChatId().equals(chatId))
            .sorted((a, b) -> {
                if (!a.getReportYear().equals(b.getReportYear())) {
                    return b.getReportYear().compareTo(a.getReportYear());
                }
                return b.getReportMonth().compareTo(a.getReportMonth());
            })
            .toList();
    }
}
