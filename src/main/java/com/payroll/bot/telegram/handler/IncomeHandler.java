package com.payroll.bot.telegram.handler;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.entity.TransactionRecord;
import com.payroll.bot.service.CalculationService;
import com.payroll.bot.service.UserService;
import com.payroll.bot.telegram.BotState;
import com.payroll.bot.telegram.KeyboardUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;

@Component
public class IncomeHandler extends BaseHandler {

    private final UserService userService;
    private final CalculationService calculationService;

    public IncomeHandler(UserService userService, CalculationService calculationService) {
        this.userService = userService;
        this.calculationService = calculationService;
    }

    @Override
    public boolean canHandle(BotSession session, Update update) {
        BotState state = session.getState();
        if (state == BotState.ADD_INCOME_DATE || state == BotState.ADD_INCOME_SERVICE || state == BotState.ADD_INCOME_VALUE) return true;
        if (state == BotState.EDIT_TRANSACTION_VALUE) return true;

        String text = getText(update);
        if ("💰 Добавить доход".equals(text)) return true;

        String data = getData(update);
        if (data != null) {
            if (data.startsWith("EDIT_TX_")) return true;
            if (state == BotState.ADD_INCOME_DATE && (data.startsWith("DATE_") || data.startsWith("CAL_DAY_") || data.startsWith("CAL_PREV_") || data.startsWith("CAL_NEXT_"))) {
                return true;
            }
            if (state == BotState.ADD_INCOME_SERVICE && (data.startsWith("SRV_DYN_") || data.equals("SRV_HOURLY"))) {
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

        if ("💰 Добавить доход".equals(text)) {
            session.setState(BotState.ADD_INCOME_DATE);
            SendMessage msg = createMessage(chatId, "Выберите дату дохода:");
            msg.setReplyMarkup(KeyboardUtils.getDateSelectionKeyboard());
            return msg;
        }

        if (data != null && data.startsWith("EDIT_TX_")) {
            Long txId = Long.parseLong(data.substring("EDIT_TX_".length()));
            TransactionRecord record = calculationService.getTransactionById(txId);
            if (calculationService.isMonthClosed(chatId, record.getTransactionDate().getMonthValue(), record.getTransactionDate().getYear())) {
                return createMessage(chatId, "❌ Этот месяц закрыт, редактирование невозможно.");
            }
            session.setTempTransactionId(txId);
            session.setState(BotState.EDIT_TRANSACTION_VALUE);
            return createMessage(chatId, "Введите новую сумму для этой услуги (например 120):");
        }

        if (session.getState() == BotState.ADD_INCOME_DATE && data != null) {
            if ("DATE_TODAY".equals(data)) {
                session.setTempDate(LocalDate.now());
            } else if ("DATE_YESTERDAY".equals(data)) {
                session.setTempDate(LocalDate.now().minusDays(1));
            } else if ("DATE_CALENDAR".equals(data)) {
                SendMessage msg = createMessage(chatId, "Выберите день:");
                msg.setReplyMarkup(KeyboardUtils.getCalendarKeyboard(LocalDate.now(), null));
                return msg;
            } else if (data.startsWith("CAL_DAY_")) {
                LocalDate date = LocalDate.parse(data.substring("CAL_DAY_".length()));
                session.setTempDate(date);
            } else if (data.startsWith("CAL_PREV_") || data.startsWith("CAL_NEXT_")) {
                LocalDate date = LocalDate.parse(data.substring(9));
                SendMessage msg = createMessage(chatId, "Выберите день:");
                msg.setReplyMarkup(KeyboardUtils.getCalendarKeyboard(date, null));
                return msg;
            }

            if ("DATE_TODAY".equals(data) || "DATE_YESTERDAY".equals(data) || data.startsWith("CAL_DAY_")) {
                session.setState(BotState.ADD_INCOME_SERVICE);
                SendMessage msg = createMessage(chatId, "Выбрана дата: " + session.getTempDate() + "\nВыберите услугу:");
                msg.setReplyMarkup(KeyboardUtils.getDynamicServiceSelectionKeyboard(userService.getServiceCategories(chatId)));
                return msg;
            }
        }

        if (session.getState() == BotState.ADD_INCOME_SERVICE && data != null) {
            if (data.startsWith("SRV_DYN_")) {
                Long srvId = Long.parseLong(data.substring("SRV_DYN_".length()));
                com.payroll.bot.entity.ServiceCategory svc = userService.getServiceCategories(chatId).stream()
                        .filter(s -> s.getId().equals(srvId))
                        .findFirst()
                        .orElse(null);
                if (svc != null) {
                    session.setTempServiceType(svc.getName());
                    session.setState(BotState.ADD_INCOME_VALUE);
                    return createMessage(chatId, "Введите стоимость услуги '" + svc.getName() + "' в евро (полную сумму):");
                }
            } else if ("SRV_HOURLY".equals(data)) {
                session.setTempServiceType("HOURLY");
                session.setState(BotState.ADD_INCOME_VALUE);
                return createMessage(chatId, "Введите временной интервал (формат HH:mm-HH:mm, например 13:00-15:30):");
            }
        }

        if (session.getState() == BotState.ADD_INCOME_VALUE) {
            if (text == null) {
                return createMessage(chatId, "Пожалуйста, отправьте текстовое значение или сумму.");
            }
            try {
                TransactionRecord record = calculationService.addIncome(chatId, session.getTempDate(), session.getTempServiceType(), text);
                session.setState(BotState.MAIN_MENU);
                String responseMsg = "✅ Доход успешно добавлен!\n\n🔹 Услуга: " + record.getServiceType() + "\n";
                if ("HOURLY".equals(record.getServiceType())) {
                    responseMsg += "🔹 Отработано часов: " + record.getInputValue() + "\n";
                } else {
                    responseMsg += "🔹 Стоимость услуги: " + record.getInputValue() + " €\n";
                }
                responseMsg += "💰 Ваш заработок: <b>" + record.getEarnedAmount() + " €</b>";
                return createMessage(chatId, responseMsg);
            } catch (Exception e) {
                return createMessage(chatId, "Ошибка: " + e.getMessage() + "\nПопробуйте еще раз или нажмите /start для отмены.");
            }
        }

        if (session.getState() == BotState.EDIT_TRANSACTION_VALUE) {
            if (text == null) {
                return createMessage(chatId, "Пожалуйста, введите числовое значение суммы.");
            }
            try {
                calculationService.updateTransaction(session.getTempTransactionId(), text);
                session.setState(BotState.MAIN_MENU);
                return createMessage(chatId, "✅ Запись успешно обновлена!");
            } catch (Exception e) {
                return createMessage(chatId, "Пожалуйста, введите корректное число.");
            }
        }

        return null;
    }
}
