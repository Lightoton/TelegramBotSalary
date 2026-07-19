package com.payroll.bot.service;

import com.payroll.bot.entity.UserSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private CalculationService calculationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private BudgetService budgetService;

    private final Long chatId = 12345L;

    @Test
    void generateBudgetReport_WhenFreeMoneyPositive_ShouldContainSuccessMessage() {
        when(calculationService.getMonthlyTotal(chatId, 7, 2026)).thenReturn(new BigDecimal("1000.00"));
        
        UserSettings settings = new UserSettings();
        settings.setRentLimit(new BigDecimal("500"));
        settings.setFoodLimit(new BigDecimal("200"));
        settings.setAdditionalLimit(new BigDecimal("100"));
        settings.setMonthlyTax(new BigDecimal("50"));
        
        when(userService.getUserSettings(chatId)).thenReturn(settings);

        String report = budgetService.generateBudgetReport(chatId, 7, 2026);

        assertTrue(report.contains("Всего расходов: 850"));
        assertTrue(report.contains("✅ Свободные деньги: <b>150"));
    }

    @Test
    void generateBudgetReport_WhenFreeMoneyNegative_ShouldContainErrorMessage() {
        when(calculationService.getMonthlyTotal(chatId, 7, 2026)).thenReturn(new BigDecimal("500.00"));
        
        UserSettings settings = new UserSettings();
        settings.setRentLimit(new BigDecimal("500"));
        settings.setFoodLimit(new BigDecimal("200"));
        settings.setAdditionalLimit(new BigDecimal("100"));
        settings.setMonthlyTax(new BigDecimal("50"));
        
        when(userService.getUserSettings(chatId)).thenReturn(settings);

        String report = budgetService.generateBudgetReport(chatId, 7, 2026);

        assertTrue(report.contains("Всего расходов: 850"));
        assertTrue(report.contains("❌ Не хватает до покрытия базы: <b>350"));
    }
}
