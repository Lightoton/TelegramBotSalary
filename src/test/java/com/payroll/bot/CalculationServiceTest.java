package com.payroll.bot;

import com.payroll.bot.entity.MonthlyReport;
import com.payroll.bot.entity.ServiceCategory;
import com.payroll.bot.entity.TransactionRecord;
import com.payroll.bot.entity.UserSettings;
import com.payroll.bot.repository.MonthlyReportRepository;
import com.payroll.bot.repository.TransactionRecordRepository;
import com.payroll.bot.service.CalculationService;
import com.payroll.bot.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CalculationServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private TransactionRecordRepository transactionRepository;

    @Mock
    private MonthlyReportRepository monthlyReportRepository;

    @InjectMocks
    private CalculationService calculationService;

    private UserSettings mockSettings;
    private List<ServiceCategory> mockCategories;

    @BeforeEach
    void setUp() {
        mockSettings = new UserSettings();
        mockSettings.setChatId(123L);
        mockSettings.setHourlyRate(new BigDecimal("12.50"));

        mockCategories = new ArrayList<>();
        ServiceCategory laser = new ServiceCategory();
        laser.setName("LAZER");
        laser.setPercentage(new BigDecimal("40"));
        mockCategories.add(laser);
    }

    @Test
    void testAddIncomeLaser() {
        when(userService.getUserSettings(anyLong())).thenReturn(mockSettings);
        when(userService.getServiceCategories(anyLong())).thenReturn(mockCategories);

        calculationService.addIncome(123L, LocalDate.now(), "LAZER", "100");

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRepository, times(1)).save(captor.capture());

        TransactionRecord saved = captor.getValue();
        assertEquals(new BigDecimal("100"), saved.getInputValue());
        assertEquals(new BigDecimal("40.00"), saved.getEarnedAmount());
        assertEquals("LAZER", saved.getServiceType());
    }

    @Test
    void testAddIncome_UnknownServiceThrowsException() {
        when(userService.getUserSettings(anyLong())).thenReturn(mockSettings);
        when(userService.getServiceCategories(anyLong())).thenReturn(mockCategories);

        assertThrows(IllegalArgumentException.class, () ->
                calculationService.addIncome(123L, LocalDate.now(), "UNKNOWN_SERVICE", "100")
        );
    }

    @Test
    void testAddIncomeHourly() {
        when(userService.getUserSettings(anyLong())).thenReturn(mockSettings);

        calculationService.addIncome(123L, LocalDate.now(), "HOURLY", "13:00-15:30");

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRepository, times(1)).save(captor.capture());

        TransactionRecord saved = captor.getValue();
        assertEquals(new BigDecimal("2.50"), saved.getInputValue());
        assertEquals(new BigDecimal("31.25"), saved.getEarnedAmount());
        assertEquals("HOURLY", saved.getServiceType());
    }

    @Test
    void testAddIncomeHourly_InvalidFormatThrowsException() {
        when(userService.getUserSettings(anyLong())).thenReturn(mockSettings);

        assertThrows(IllegalArgumentException.class, () ->
                calculationService.addIncome(123L, LocalDate.now(), "HOURLY", "invalid-interval")
        );
    }

    @Test
    void testAddIncomeHourlyOvernight() {
        when(userService.getUserSettings(anyLong())).thenReturn(mockSettings);

        calculationService.addIncome(123L, LocalDate.now(), "HOURLY", "23:00-01:30");

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRepository, times(1)).save(captor.capture());

        TransactionRecord saved = captor.getValue();
        assertEquals(new BigDecimal("2.50"), saved.getInputValue());
        assertEquals(new BigDecimal("31.25"), saved.getEarnedAmount());
    }

    @Test
    void testGetTransactionById_FoundAndNotFound() {
        TransactionRecord record = new TransactionRecord(1L, 123L, "LAZER", new BigDecimal("100"), new BigDecimal("40.00"), LocalDate.now(), LocalDateTime.now());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(record));
        when(transactionRepository.findById(2L)).thenReturn(Optional.empty());

        assertEquals(record, calculationService.getTransactionById(1L));
        assertThrows(IllegalArgumentException.class, () -> calculationService.getTransactionById(2L));
    }

    @Test
    void testUpdateTransaction_HourlyAndService() {
        TransactionRecord hourlyTx = new TransactionRecord(1L, 123L, "HOURLY", new BigDecimal("1.00"), new BigDecimal("12.50"), LocalDate.now(), LocalDateTime.now());
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(hourlyTx));
        when(userService.getUserSettings(123L)).thenReturn(mockSettings);

        calculationService.updateTransaction(1L, "10:00-12:00");
        assertEquals(new BigDecimal("2.00"), hourlyTx.getInputValue());
        assertEquals(new BigDecimal("25.00"), hourlyTx.getEarnedAmount());
        verify(transactionRepository).save(hourlyTx);

        TransactionRecord catTx = new TransactionRecord(2L, 123L, "LAZER", new BigDecimal("50"), new BigDecimal("20.00"), LocalDate.now(), LocalDateTime.now());
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(catTx));
        when(userService.getServiceCategories(123L)).thenReturn(mockCategories);

        calculationService.updateTransaction(2L, "200");
        assertEquals(new BigDecimal("200"), catTx.getInputValue());
        assertEquals(new BigDecimal("80.00"), catTx.getEarnedAmount());
    }

    @Test
    void testUpdateTransaction_UnknownServiceThrows() {
        TransactionRecord unknownTx = new TransactionRecord(3L, 123L, "UNKNOWN", new BigDecimal("50"), new BigDecimal("20.00"), LocalDate.now(), LocalDateTime.now());
        when(transactionRepository.findById(3L)).thenReturn(Optional.of(unknownTx));
        when(userService.getUserSettings(123L)).thenReturn(mockSettings);
        when(userService.getServiceCategories(123L)).thenReturn(mockCategories);

        assertThrows(IllegalArgumentException.class, () -> calculationService.updateTransaction(3L, "100"));
    }

    @Test
    void testGetMonthlyTotal_PresentAndEmpty() {
        when(transactionRepository.sumEarnedAmountByChatIdAndMonthAndYear(123L, 5, 2026))
                .thenReturn(Optional.of(new BigDecimal("500.00")));
        when(transactionRepository.sumEarnedAmountByChatIdAndMonthAndYear(123L, 6, 2026))
                .thenReturn(Optional.empty());

        assertEquals(new BigDecimal("500.00"), calculationService.getMonthlyTotal(123L, 5, 2026));
        assertEquals(BigDecimal.ZERO, calculationService.getMonthlyTotal(123L, 6, 2026));
    }

    @Test
    void testDailyAndMonthlyTransactions() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        List<TransactionRecord> list = List.of(new TransactionRecord());
        when(transactionRepository.findAllByChatIdAndTransactionDate(123L, date)).thenReturn(list);
        when(transactionRepository.findAllByChatIdAndTransactionDateBetween(123L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(list);

        assertEquals(list, calculationService.getDailyTransactions(123L, date));
        assertEquals(list, calculationService.getTransactionsByMonth(123L, 10, 2026));
    }

    @Test
    void testCloseMonth_ShouldSaveReportAndThrowIfAlreadyClosed() {
        when(monthlyReportRepository.findByChatIdAndReportMonthAndReportYear(123L, 7, 2026)).thenReturn(Optional.empty());
        when(transactionRepository.sumEarnedAmountByChatIdAndMonthAndYear(123L, 7, 2026)).thenReturn(Optional.of(new BigDecimal("1000.00")));

        calculationService.closeMonth(123L, 7, 2026);

        ArgumentCaptor<MonthlyReport> captor = ArgumentCaptor.forClass(MonthlyReport.class);
        verify(monthlyReportRepository, times(1)).save(captor.capture());

        MonthlyReport saved = captor.getValue();
        assertEquals(7, saved.getReportMonth());
        assertEquals(2026, saved.getReportYear());
        assertEquals(new BigDecimal("1000.00"), saved.getTotalEarned());

        // Already closed check
        when(monthlyReportRepository.findByChatIdAndReportMonthAndReportYear(123L, 7, 2026)).thenReturn(Optional.of(saved));
        assertThrows(IllegalStateException.class, () -> calculationService.closeMonth(123L, 7, 2026));
    }

    @Test
    void testIsMonthClosed() {
        MonthlyReport report = new MonthlyReport();
        report.setChatId(123L);
        report.setReportMonth(7);
        report.setReportYear(2026);

        when(monthlyReportRepository.findByChatIdAndReportMonthAndReportYear(123L, 7, 2026)).thenReturn(Optional.of(report));
        when(monthlyReportRepository.findByChatIdAndReportMonthAndReportYear(123L, 8, 2026)).thenReturn(Optional.empty());

        assertTrue(calculationService.isMonthClosed(123L, 7, 2026));
        assertFalse(calculationService.isMonthClosed(123L, 8, 2026));
    }

    @Test
    void testGetUnclosedMonths_SortingAndFiltering() {
        Long chatId = 123L;
        TransactionRecord t1 = new TransactionRecord(1L, chatId, "SRV", BigDecimal.TEN, BigDecimal.TEN, LocalDate.of(2025, 12, 10), LocalDateTime.now());
        TransactionRecord t2 = new TransactionRecord(2L, chatId, "SRV", BigDecimal.TEN, BigDecimal.TEN, LocalDate.of(2026, 1, 15), LocalDateTime.now());
        TransactionRecord t3 = new TransactionRecord(3L, chatId, "SRV", BigDecimal.TEN, BigDecimal.TEN, LocalDate.of(2026, 2, 20), LocalDateTime.now());
        when(transactionRepository.findAllByChatId(chatId)).thenReturn(List.of(t1, t2, t3));

        // 2025-12 is closed
        MonthlyReport rep = new MonthlyReport(1L, chatId, 12, 2025, BigDecimal.TEN, LocalDateTime.now());
        when(monthlyReportRepository.findAllByChatIdOrderByReportYearDescReportMonthDesc(chatId)).thenReturn(List.of(rep));

        List<YearMonth> unclosed = calculationService.getUnclosedMonths(chatId);
        assertEquals(2, unclosed.size());
        assertEquals(YearMonth.of(2026, 2), unclosed.get(0));
        assertEquals(YearMonth.of(2026, 1), unclosed.get(1));
    }
}
