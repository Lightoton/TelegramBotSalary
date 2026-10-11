package com.payroll.bot.telegram.handler;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.entity.MonthlyReport;
import com.payroll.bot.entity.TransactionRecord;
import com.payroll.bot.entity.UserSettings;
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
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MainMenuHandler extends BaseHandler {

    private final UserService userService;
    private final CalculationService calculationService;

    public MainMenuHandler(UserService userService, CalculationService calculationService) {
        this.userService = userService;
        this.calculationService = calculationService;
    }

    @Override
    public boolean canHandle(BotSession session, Update update) {
        String text = getText(update);
        if ("/start".equals(text) || "/menu".equals(text) || "📊 Статистика".equals(text) 
                || "📄 Подробная статистика".equals(text) || "📁 История".equals(text) || "📚 Архив месяцев".equals(text) || "🔒 Закрыть месяц".equals(text)) {
            return true;
        }
        
        String data = getData(update);
        if (data != null) {
            if ("CANCEL".equals(data) || "IGNORE".equals(data)) return true;
            if (data.equals("STAT_MENU") || data.startsWith("STAT_")) return true;
            if (data.startsWith("CLOSE_MONTH_SELECT_") || data.startsWith("CONFIRM_CLOSE_MONTH_") || "CANCEL_CLOSE_MONTH".equals(data)) return true;
            if (data.startsWith("ARCHIVE_") || data.startsWith("HISTORY_") || data.startsWith("HISTORY_DETAIL_")) return true;
            if (session.getState() == BotState.MAIN_MENU && (data.startsWith("CAL_DAY_") || data.startsWith("CAL_PREV_") || data.startsWith("CAL_NEXT_"))) {
                return true;
            }
        }
        
        return false;
    }

    @Override
    public SendMessage handle(Update update, BotSession session) {
        Long chatId = getChatId(update);
        String text = getText(update);
        String data = getData(update);

        if ("CANCEL".equals(data)) {
            session.setState(BotState.MAIN_MENU);
            SendMessage msg = createMessage(chatId, "Отменено. Выберите действие:");
            msg.setReplyMarkup(KeyboardUtils.getMainMenu());
            return msg;
        }

        if ("IGNORE".equals(data)) {
            return createMessage(chatId, "⚠️ Это действие недоступно.");
        }

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
            SendMessage msg = createMessage(chatId, statStr);
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText("Подробная статистика");
            btn.setCallbackData("STAT_MENU");
            row.add(btn);
            rows.add(row);
            markup.setKeyboard(rows);
            msg.setReplyMarkup(markup);
            return msg;
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

        if ("📁 История".equals(text) || "📚 Архив месяцев".equals(text)) {
            List<MonthlyReport> archived = calculationService.getArchivedMonths(chatId);
            if (archived.isEmpty()) return createMessage(chatId, "У вас пока нет закрытых месяцев в истории.");
            SendMessage msg = createMessage(chatId, "Архив закрытых месяцев:\n(Нажмите на месяц, чтобы посмотреть детали)");
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            for (MonthlyReport rep : archived) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton btn = new InlineKeyboardButton();
                btn.setText(rep.getReportMonth() + "/" + rep.getReportYear() + " — " + rep.getTotalEarned() + " €");
                btn.setCallbackData("HISTORY_" + rep.getReportMonth() + "_" + rep.getReportYear());
                row.add(btn);
                rows.add(row);
            }
            markup.setKeyboard(rows);
            msg.setReplyMarkup(markup);
            return msg;
        }

        if (data != null && data.startsWith("HISTORY_DETAIL_")) {
            String[] parts = data.split("_");
            int m = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            List<TransactionRecord> txs = calculationService.getTransactionsByMonth(chatId, m, y);
            if (txs.isEmpty()) {
                return createMessage(chatId, "Транзакции не найдены.");
            }
            StringBuilder sb = new StringBuilder("Детализация за " + m + "/" + y + ":\n");
            for (TransactionRecord tx : txs) {
                sb.append(tx.getTransactionDate()).append(" | ").append(tx.getServiceType()).append(" | ").append(tx.getEarnedAmount()).append(" €\n");
            }
            return createMessage(chatId, sb.toString());
        }

        if (data != null && (data.startsWith("ARCHIVE_") || data.startsWith("HISTORY_"))) {
            String[] parts = data.split("_");
            int m = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            MonthlyReport rep = calculationService.getArchivedMonths(chatId).stream()
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

        if (data != null && data.startsWith("CLOSE_MONTH_SELECT_")) {
            String[] parts = data.split("_");
            int month = Integer.parseInt(parts[3]);
            int year = Integer.parseInt(parts[4]);
            String monthName = Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));
            String btnText = monthName.substring(0, 1).toUpperCase() + monthName.substring(1) + " " + year;
            
            SendMessage msg = createMessage(chatId, "Вы уверены, что хотите закрыть <b>" + btnText + "</b>?");
            msg.setReplyMarkup(KeyboardUtils.getCloseMonthConfirmationKeyboard(month, year));
            return msg;
        }

        if (data != null && data.startsWith("CONFIRM_CLOSE_MONTH_")) {
            try {
                String[] parts = data.split("_");
                int month = Integer.parseInt(parts[3]);
                int year = Integer.parseInt(parts[4]);
                calculationService.closeMonth(chatId, month, year);
                return createMessage(chatId, "✅ Месяц успешно закрыт и добавлен в архив!");
            } catch (Exception e) {
                return createMessage(chatId, "❌ " + e.getMessage());
            }
        }

        if ("CANCEL_CLOSE_MONTH".equals(data)) {
            session.setState(BotState.MAIN_MENU);
            SendMessage msg = createMessage(chatId, "Действие отменено.");
            msg.setReplyMarkup(KeyboardUtils.getMainMenu());
            return msg;
        }

        return null;
    }

    private SendMessage showDetailedStatsForDate(Long chatId, LocalDate date) {
        List<TransactionRecord> txs = calculationService.getDailyTransactions(chatId, date);
        if (txs.isEmpty()) {
            return createMessage(chatId, "Нет записей за " + date);
        }

        BigDecimal dailyTotal = txs.stream()
                .map(TransactionRecord::getEarnedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SendMessage msg = createMessage(chatId, "Транзакции за " + date + ":\nЗаработано за день: " + dailyTotal + " €\nВыберите запись для редактирования:");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        int index = 1;
        for (TransactionRecord tx : txs) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(index + ". " + tx.getServiceType() + " | В: " + tx.getInputValue() + " | € %: " + tx.getEarnedAmount() + " 💶");
            if (calculationService.isMonthClosed(chatId, date.getMonthValue(), date.getYear())) {
                btn.setCallbackData("IGNORE");
                btn.setText("🔒 " + btn.getText());
            } else {
                btn.setCallbackData("EDIT_TX_" + tx.getId());
            }
            row.add(btn);
            rows.add(row);
            index++;
        }

        List<InlineKeyboardButton> cancelRow = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText("Отмена");
        cancelBtn.setCallbackData("CANCEL");
        cancelRow.add(cancelBtn);
        rows.add(cancelRow);

        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }
}
