package com.payroll.bot.telegram.handler;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.entity.TransactionRecord;
import com.payroll.bot.entity.UserSettings;
import com.payroll.bot.service.BudgetService;
import com.payroll.bot.service.CalculationService;
import com.payroll.bot.service.UserService;
import com.payroll.bot.telegram.BotState;
import com.payroll.bot.telegram.KeyboardUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class MainMenuHandler extends BaseHandler {

    private final UserService userService;
    private final CalculationService calculationService;
    private final BudgetService budgetService;

    public MainMenuHandler(UserService userService, CalculationService calculationService, BudgetService budgetService) {
        this.userService = userService;
        this.calculationService = calculationService;
        this.budgetService = budgetService;
    }

    @Override
    public boolean canHandle(BotSession session, Update update) {
        String text = getText(update);
        if ("/start".equals(text) || "/menu".equals(text) || "📊 Статистика".equals(text) || "📄 Подробная статистика".equals(text) || "📚 Архив месяцев".equals(text) || "🔒 Закрыть месяц".equals(text)) return true;
        
        String data = getData(update);
        if (data != null && (data.startsWith("CLOSE_MONTH_") || data.startsWith("ARCHIVE_") || data.equals("STAT_MENU") || data.startsWith("STAT_") || data.startsWith("HISTORY_DETAIL_") || (data.startsWith("CAL_DAY_") && session.getState() == BotState.MAIN_MENU) || (data.startsWith("CAL_PREV_") && session.getState() == BotState.MAIN_MENU) || (data.startsWith("CAL_NEXT_") && session.getState() == BotState.MAIN_MENU))) return true;
        
        return false;
    }

    @Override
    public SendMessage handle(Update update, BotSession session) {
        Long chatId = getChatId(update);
        String text = getText(update);
        String data = getData(update);

        if ("/start".equals(text) || "/menu".equals(text)) {
            session.setState(BotState.MAIN_MENU);
            SendMessage msg = createMessage(chatId, "🤖 Главное меню открыто!\n\n👆 Пожалуйста, не удаляйте это сообщение, оно держит нижние кнопки.");
            msg.setReplyMarkup(KeyboardUtils.getMainMenu());
            return msg;
        }

        if ("📊 Статистика".equals(text)) {
            LocalDate now = LocalDate.now();
            BigDecimal monthTotal = calculationService.getMonthlyTotal(chatId, now.getMonthValue(), now.getYear());
            UserSettings settings = userService.getUserSettings(chatId);
            BigDecimal currentTax = settings.getMonthlyTax() != null ? settings.getMonthlyTax() : BigDecimal.ZERO;
            
            String statStr = "📊 <b>Статистика за текущий месяц</b> (" + now.getMonthValue() + "/" + now.getYear() + ")\n\n" +
                             "💰 Всего заработано: <b>" + monthTotal + " €</b>\n" +
                             "📉 Налог: <b>" + currentTax + " €</b>\n" +
                             "💵 Чистая прибыль: <b>" + monthTotal.subtract(currentTax) + " €</b>";
            return createMessage(chatId, statStr);
        }

        if ("📄 Подробная статистика".equals(text) || (data != null && data.equals("STAT_MENU"))) {
            session.setState(BotState.MAIN_MENU);
            SendMessage msg = createMessage(chatId, "За какой день показать статистику?");
            msg.setReplyMarkup(KeyboardUtils.getStatisticsDateSelectionKeyboard());
            return msg;
        }
        
        if (data != null && data.startsWith("STAT_")) {
            if ("STAT_TODAY".equals(data)) {
                return showDetailedStatsForDate(chatId, LocalDate.now());
            } else if ("STAT_YESTERDAY".equals(data)) {
                return showDetailedStatsForDate(chatId, LocalDate.now().minusDays(1));
            } else if ("STAT_CALENDAR".equals(data)) {
                LocalDate now = LocalDate.now();
                List<LocalDate> activeDates = calculationService.getTransactionsByMonth(chatId, now.getMonthValue(), now.getYear())
                        .stream().map(TransactionRecord::getTransactionDate).distinct().toList();
                SendMessage msg = createMessage(chatId, "Выберите день:");
                msg.setReplyMarkup(KeyboardUtils.getCalendarKeyboard(now, activeDates));
                session.setState(BotState.MAIN_MENU);
                return msg;
            }
        }
        
        if (data != null && session.getState() == BotState.MAIN_MENU && (data.startsWith("CAL_DAY_") || data.startsWith("CAL_PREV_") || data.startsWith("CAL_NEXT_"))) {
             if (data.startsWith("CAL_DAY_")) {
                LocalDate date = LocalDate.parse(data.substring("CAL_DAY_".length()));
                return showDetailedStatsForDate(chatId, date);
             } else {
                LocalDate date = LocalDate.parse(data.substring(9));
                SendMessage msg = createMessage(chatId, "Выберите день:");
                List<LocalDate> activeDates = calculationService.getTransactionsByMonth(chatId, date.getMonthValue(), date.getYear())
                        .stream().map(TransactionRecord::getTransactionDate).distinct().toList();
                msg.setReplyMarkup(KeyboardUtils.getCalendarKeyboard(date, activeDates));
                return msg;
             }
        }

        if ("📚 Архив месяцев".equals(text)) {
            List<com.payroll.bot.entity.MonthlyReport> archived = calculationService.getArchivedMonths(chatId);
            if (archived.isEmpty()) return createMessage(chatId, "У вас пока нет закрытых месяцев.");
            SendMessage msg = createMessage(chatId, "Выберите закрытый месяц для просмотра отчета:");
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            for (com.payroll.bot.entity.MonthlyReport rep : archived) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton btn = new InlineKeyboardButton();
                btn.setText(rep.getReportMonth() + "/" + rep.getReportYear());
                btn.setCallbackData("ARCHIVE_" + rep.getReportMonth() + "_" + rep.getReportYear());
                row.add(btn);
                rows.add(row);
            }
            markup.setKeyboard(rows);
            msg.setReplyMarkup(markup);
            return msg;
        }

        if (data != null && data.startsWith("ARCHIVE_")) {
            String[] parts = data.split("_");
            int m = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            com.payroll.bot.entity.MonthlyReport rep = calculationService.getArchivedMonths(chatId).stream()
                .filter(r -> r.getReportMonth() == m && r.getReportYear() == y).findFirst().orElse(null);
            if (rep != null) {
                SendMessage msg = createMessage(chatId, "Архив " + m + "/" + y + "\nВсего заработано: " + rep.getTotalEarned() + " €");
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton btn = new InlineKeyboardButton();
                btn.setText("Детальная зарплата за месяц");
                btn.setCallbackData("HISTORY_DETAIL_" + m + "_" + y);
                row.add(btn);
                rows.add(row);
                markup.setKeyboard(rows);
                msg.setReplyMarkup(markup);
                return msg;
            }
        }

        if ("🔒 Закрыть месяц".equals(text)) {
            List<YearMonth> unclosed = calculationService.getUnclosedMonths(chatId);
            if (unclosed.isEmpty()) return createMessage(chatId, "У вас нет открытых месяцев для закрытия.");
            SendMessage msg = createMessage(chatId, "Выберите месяц для закрытия:\nВнимание: после закрытия месяца редактирование доходов будет недоступно!");
            msg.setReplyMarkup(KeyboardUtils.getUnclosedMonthsKeyboard(unclosed));
            return msg;
        }

        if (data != null && data.startsWith("CLOSE_MONTH_")) {
            String[] parts = data.substring("CLOSE_MONTH_".length()).split("_");
            int m = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            calculationService.closeMonth(chatId, m, y);
            return createMessage(chatId, "✅ Месяц " + m + "/" + y + " успешно закрыт и перенесен в архив!");
        }

        return null;
    }

    private SendMessage showDetailedStatsForDate(Long chatId, LocalDate date) {
        List<TransactionRecord> transactions = calculationService.getDailyTransactions(chatId, date);
        if (transactions.isEmpty()) {
            return createMessage(chatId, "За " + date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " транзакций не найдено.");
        }
        
        BigDecimal totalDay = transactions.stream().map(TransactionRecord::getEarnedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder();
        sb.append("📋 <b>Транзакции за ").append(date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append("</b>\n");
        sb.append("💰 Заработано за день: <b>").append(totalDay).append(" €</b>\n\n");
        sb.append("Выберите запись для редактирования:");

        SendMessage msg = createMessage(chatId, sb.toString());
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (TransactionRecord tx : transactions) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            String timeStr = tx.getTransactionDate().format(DateTimeFormatter.ofPattern("HH:mm"));
            btn.setText(timeStr + " | " + tx.getServiceType() + " | " + tx.getEarnedAmount() + "€");
            btn.setCallbackData("EDIT_TX_" + tx.getId());
            row.add(btn);
            rows.add(row);
        }
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        
        return msg;
    }
}
