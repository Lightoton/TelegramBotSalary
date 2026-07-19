package com.payroll.bot.service;

import com.payroll.bot.entity.BudgetCategory;
import com.payroll.bot.repository.BudgetCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetService {
    
    private final BudgetCategoryRepository budgetRepository;
    private final CalculationService calculationService;
    private final UserService userService;

    public String generateBudgetReport(Long chatId, int month, int year) {
        BigDecimal totalEarned = calculationService.getMonthlyTotal(chatId, month, year);
        com.payroll.bot.entity.UserSettings settings = userService.getUserSettings(chatId);
        
        BigDecimal rent = settings.getRentLimit().compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : settings.getRentLimit();
        BigDecimal food = settings.getFoodLimit().compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : settings.getFoodLimit();
        BigDecimal additional = settings.getAdditionalLimit().compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : settings.getAdditionalLimit();
        BigDecimal tax = settings.getMonthlyTax();
        
        BigDecimal totalLimits = rent.add(food).add(additional).add(tax);
        BigDecimal freeMoney = totalEarned.subtract(totalLimits);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 <b>Бюджет на месяц</b>\n\n");
        sb.append("💰 Заработано: ").append(totalEarned).append(" €\n");
        sb.append("🏠 Аренда и ЖКУ: ").append(rent).append(" €\n");
        sb.append("🍔 Еда: ").append(food).append(" €\n");
        sb.append("🛍 Дополнительно: ").append(additional).append(" €\n");
        sb.append("🧾 Налог: ").append(tax).append(" €\n");
        sb.append("----------------------------\n");
        sb.append("Всего расходов: ").append(totalLimits).append(" €\n");
        if (freeMoney.compareTo(BigDecimal.ZERO) >= 0) {
            sb.append("✅ Свободные деньги: <b>").append(freeMoney).append(" €</b>\n");
        } else {
            sb.append("❌ Не хватает до покрытия базы: <b>").append(freeMoney.abs()).append(" €</b>\n");
        }
        return sb.toString();
    }
}
