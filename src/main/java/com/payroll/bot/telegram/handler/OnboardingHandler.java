package com.payroll.bot.telegram.handler;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.service.MessageService;
import com.payroll.bot.service.UserService;
import com.payroll.bot.telegram.BotState;
import com.payroll.bot.telegram.KeyboardUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;

@Component
public class OnboardingHandler extends BaseHandler {

    private final UserService userService;
    private final MessageService messageService;
    private final String masterPassword;

    public OnboardingHandler(UserService userService, MessageService messageService, @Value("${app.security.master-password}") String masterPassword) {
        this.userService = userService;
        this.messageService = messageService;
        this.masterPassword = masterPassword;
    }

    @Override
    public boolean canHandle(BotSession session, Update update) {
        BotState state = session.getState();
        if (state == BotState.AWAITING_PASSWORD) return true;
        if (state == BotState.ONBOARDING_ADD_SERVICE_NAME || state == BotState.ONBOARDING_ADD_SERVICE_PERCENTAGE) return true;
        if (state == BotState.ONBOARDING_HOURLY_RATE || state == BotState.ONBOARDING_MONTHLY_TAX) return true;
        
        String data = getData(update);
        if ("FINISH_ONBOARDING_SERVICES".equals(data)) return true;
        
        return false;
    }

    @Override
    public SendMessage handle(Update update, BotSession session) {
        Long chatId = getChatId(update);
        String text = getText(update);
        String data = getData(update);

        if ("FINISH_ONBOARDING_SERVICES".equals(data)) {
            session.setState(BotState.ONBOARDING_HOURLY_RATE);
            return createMessage(chatId, messageService.getMessage("onboarding.hourly_rate"));
        }

        if (text == null) {
            return createMessage(chatId, "Пожалуйста, отправьте текстовое сообщение или число.");
        }

        switch (session.getState()) {
            case AWAITING_PASSWORD:
                if (masterPassword.equals(text)) {
                    userService.registerUser(chatId);
                    session.setState(BotState.ONBOARDING_ADD_SERVICE_NAME);
                    return createMessage(chatId, messageService.getMessage("onboarding.welcome"));
                } else {
                    return createMessage(chatId, messageService.getMessage("onboarding.wrong_password"));
                }
            case ONBOARDING_ADD_SERVICE_NAME:
                session.setTempServiceName(text);
                session.setState(BotState.ONBOARDING_ADD_SERVICE_PERCENTAGE);
                return createMessage(chatId, messageService.getMessage("onboarding.service.percentage", text));
            case ONBOARDING_ADD_SERVICE_PERCENTAGE:
                try {
                    BigDecimal percent = new BigDecimal(text.replace(",", "."));
                    userService.addServiceCategory(chatId, session.getTempServiceName(), percent);
                    session.setState(BotState.ONBOARDING_ADD_SERVICE_NAME);
                    SendMessage msg = createMessage(chatId, messageService.getMessage("onboarding.service.added"));
                    msg.setReplyMarkup(KeyboardUtils.getOnboardingFinishKeyboard());
                    return msg;
                } catch (NumberFormatException e) {
                    return createMessage(chatId, messageService.getMessage("error.number.format"));
                }
            case ONBOARDING_HOURLY_RATE:
                try {
                    BigDecimal rate = new BigDecimal(text.replace(",", "."));
                    if (rate.compareTo(BigDecimal.ZERO) < 0) {
                        return createMessage(chatId, messageService.getMessage("error.number.negative"));
                    }
                    userService.updateHourlyRate(chatId, rate);
                    session.setState(BotState.ONBOARDING_MONTHLY_TAX);
                    return createMessage(chatId, messageService.getMessage("onboarding.tax"));
                } catch (NumberFormatException e) {
                    return createMessage(chatId, messageService.getMessage("error.number.format"));
                }
            case ONBOARDING_MONTHLY_TAX:
                try {
                    BigDecimal tax = new BigDecimal(text.replace(",", "."));
                    if (tax.compareTo(BigDecimal.ZERO) < 0) {
                        return createMessage(chatId, messageService.getMessage("error.number.negative"));
                    }
                    userService.updateMonthlyTax(chatId, tax);
                    session.setState(BotState.MAIN_MENU);
                    SendMessage msg = createMessage(chatId, messageService.getMessage("onboarding.finish"));
                    msg.setReplyMarkup(KeyboardUtils.getMainMenu());
                    return msg;
                } catch (NumberFormatException e) {
                    return createMessage(chatId, messageService.getMessage("error.number.format"));
                }
            default:
                return null;
        }
    }
}
