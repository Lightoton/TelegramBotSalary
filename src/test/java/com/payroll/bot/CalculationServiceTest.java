package com.payroll.bot;

import com.payroll.bot.entity.TransactionRecord;
import com.payroll.bot.entity.UserSettings;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CalculationServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private TransactionRecordRepository transactionRepository;

    @InjectMocks
    private CalculationService calculationService;

    private UserSettings mockSettings;
    private java.util.List<com.payroll.bot.entity.ServiceCategory> mockCategories;

    @BeforeEach
    void setUp() {
        mockSettings = new UserSettings();
        mockSettings.setChatId(123L);
        mockSettings.setHourlyRate(new BigDecimal("12.50"));
        
        mockCategories = new java.util.ArrayList<>();
        com.payroll.bot.entity.ServiceCategory laser = new com.payroll.bot.entity.ServiceCategory();
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
        // 40% of 100 = 40.00
        assertEquals(new BigDecimal("40.00"), saved.getEarnedAmount());
        assertEquals("LAZER", saved.getServiceType());
    }

    @Test
    void testAddIncomeHourly() {
        when(userService.getUserSettings(anyLong())).thenReturn(mockSettings);

        calculationService.addIncome(123L, LocalDate.now(), "HOURLY", "13:00-15:30");

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRepository, times(1)).save(captor.capture());

        TransactionRecord saved = captor.getValue();
        // 2 hours 30 mins = 2.5 hours
        assertEquals(new BigDecimal("2.50"), saved.getInputValue());
        // 2.5 * 12.50 = 31.25
        assertEquals(new BigDecimal("31.25"), saved.getEarnedAmount());
        assertEquals("HOURLY", saved.getServiceType());
    }

    @Test
    void testAddIncomeHourlyOvernight() {
        when(userService.getUserSettings(anyLong())).thenReturn(mockSettings);

        // 23:00 to 01:30 is 2.5 hours
        calculationService.addIncome(123L, LocalDate.now(), "HOURLY", "23:00-01:30");

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRepository, times(1)).save(captor.capture());

        TransactionRecord saved = captor.getValue();
        assertEquals(new BigDecimal("2.50"), saved.getInputValue());
        assertEquals(new BigDecimal("31.25"), saved.getEarnedAmount());
    }

    @Test
    void testCloseMonth_ShouldSaveReport() {
        com.payroll.bot.repository.MonthlyReportRepository reportRepo = mock(com.payroll.bot.repository.MonthlyReportRepository.class);
        org.springframework.test.util.ReflectionTestUtils.setField(calculationService, "monthlyReportRepository", reportRepo);
        
        when(reportRepo.findAll()).thenReturn(java.util.Collections.emptyList());
        when(transactionRepository.sumEarnedAmountByChatIdAndMonthAndYear(123L, 7, 2026)).thenReturn(java.util.Optional.of(new BigDecimal("1000.00")));

        calculationService.closeMonth(123L, 7, 2026);

        ArgumentCaptor<com.payroll.bot.entity.MonthlyReport> captor = ArgumentCaptor.forClass(com.payroll.bot.entity.MonthlyReport.class);
        verify(reportRepo, times(1)).save(captor.capture());

        com.payroll.bot.entity.MonthlyReport saved = captor.getValue();
        assertEquals(7, saved.getReportMonth());
        assertEquals(2026, saved.getReportYear());
        assertEquals(new BigDecimal("1000.00"), saved.getTotalEarned());
    }

    @Test
    void testIsMonthClosed() {
        com.payroll.bot.repository.MonthlyReportRepository reportRepo = mock(com.payroll.bot.repository.MonthlyReportRepository.class);
        org.springframework.test.util.ReflectionTestUtils.setField(calculationService, "monthlyReportRepository", reportRepo);
        
        com.payroll.bot.entity.MonthlyReport report = new com.payroll.bot.entity.MonthlyReport();
        report.setChatId(123L);
        report.setReportMonth(7);
        report.setReportYear(2026);
        
        when(reportRepo.findAll()).thenReturn(java.util.List.of(report));

        org.junit.jupiter.api.Assertions.assertTrue(calculationService.isMonthClosed(123L, 7, 2026));
        org.junit.jupiter.api.Assertions.assertFalse(calculationService.isMonthClosed(123L, 8, 2026));
    }
}
