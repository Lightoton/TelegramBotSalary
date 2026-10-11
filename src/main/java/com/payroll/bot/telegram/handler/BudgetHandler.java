package com.payroll.bot.telegram.handler;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.entity.UserSettings;
import com.payroll.bot.service.BudgetService;
import com.payroll.bot.service.UserService;
import com.payroll.bot.telegram.BotState;
import com.payroll.bot.telegram.KeyboardUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class BudgetHandler extends BaseHandler {

    private final UserService userService;
    private final BudgetService budgetService;

    public BudgetHandler(UserService userService, BudgetService budgetService) {
        this.userService = userService;
        this.budgetService = budgetService;
    }

    @Override
    public boolean canHandle(BotSession session, Update update) {
        BotState state = session.getState();
        if (state.name().startsWith("BUDGET_SETUP_") || state.name().startsWith("EDIT_BUDGET_")) return true;

        String text = getText(update);
        if ("🏠 На жизнь".equals(text)) return true;

        String data = getData(update);
        if (data != null && data.startsWith("EDIT_BUDGET_")) return true;

        return false;
    }

    @Override
    public SendMessage handle(Update update, BotSession session) {
        Long chatId = getChatId(update);
        String text = getText(update);
        String data = getData(update);

        if ("🏠 На жизнь".equals(text)) {
            UserSettings settings = userService.getUserSettings(chatId);
            if (settings.getRentLimit().compareTo(BigDecimal.ZERO) < 0) {
                session.setState(BotState.BUDGET_SETUP_RENT);
                return createMessage(chatId, "Какая у вас аренда? В аренде, пожалуйста, укажите, помимо аренды, также коммунальные, интернет и вот это вот всё.");
            } else {
                return sendBudgetReport(chatId);
            }
        }

        if (data != null) {
            if ("EDIT_BUDGET_RENT_BTN".equals(data)) {
                session.setState(BotState.EDIT_BUDGET_RENT);
                return createMessage(chatId, "Какая у вас аренда? В аренде, пожалуйста, укажите, помимо аренды, также коммунальные, интернет и вот это вот всё:");
            }
            if ("EDIT_BUDGET_FOOD_BTN".equals(data)) {
                session.setState(BotState.EDIT_BUDGET_FOOD);
                return createMessage(chatId, "Сколько вы тратите на продукты? На еду, понятно, еда, можете с запасом указать с кафешками:");
            }
            if ("EDIT_BUDGET_ADDITIONAL_BTN".equals(data)) {
                session.setState(BotState.EDIT_BUDGET_ADDITIONAL);
                return createMessage(chatId, "Если у вас есть какие-то дополнительные траты, пожалуйста, тоже их укажите:");
            }
            return null;
        }

        if (text == null) {
            return createMessage(chatId, "Пожалуйста, введите числовое значение.");
        }

        try {
            BigDecimal value = new BigDecimal(text.replace(",", "."));
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                return createMessage(chatId, "Значение не может быть отрицательным. Пожалуйста, введите корректное число:");
            }

            switch (session.getState()) {
                case BUDGET_SETUP_RENT:
                    userService.updateRentLimit(chatId, value);
                    session.setState(BotState.BUDGET_SETUP_FOOD);
                    return createMessage(chatId, "Сколько вы тратите на продукты? На еду, понятно, еда, можете с запасом указать с кафешками.");
                case BUDGET_SETUP_FOOD:
                    userService.updateFoodLimit(chatId, value);
                    session.setState(BotState.BUDGET_SETUP_ADDITIONAL);
                    return createMessage(chatId, "Если у вас есть какие-то дополнительные траты, пожалуйста, тоже их укажите.");
                case BUDGET_SETUP_ADDITIONAL:
                    userService.updateAdditionalLimit(chatId, value);
                    session.setState(BotState.MAIN_MENU);
                    return sendBudgetReport(chatId);
                case EDIT_BUDGET_RENT:
                    userService.updateRentLimit(chatId, value);
                    session.setState(BotState.MAIN_MENU);
                    return sendBudgetReport(chatId);
                case EDIT_BUDGET_FOOD:
                    userService.updateFoodLimit(chatId, value);
                    session.setState(BotState.MAIN_MENU);
                    return sendBudgetReport(chatId);
                case EDIT_BUDGET_ADDITIONAL:
                    userService.updateAdditionalLimit(chatId, value);
                    session.setState(BotState.MAIN_MENU);
                    return sendBudgetReport(chatId);
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return createMessage(chatId, "Пожалуйста, введите число.");
        }
    }

    private SendMessage sendBudgetReport(Long chatId) {
        LocalDate now = LocalDate.now();
        String report = budgetService.generateBudgetReport(chatId, now.getMonthValue(), now.getYear());
        SendMessage msg = createMessage(chatId, report);
        msg.setReplyMarkup(KeyboardUtils.getBudgetEditKeyboard());
        return msg;
    }
}
