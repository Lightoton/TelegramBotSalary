package com.payroll.bot.telegram.handler;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.service.UserService;
import com.payroll.bot.telegram.BotState;
import com.payroll.bot.telegram.KeyboardUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;

@Component
public class SettingsHandler extends BaseHandler {

    private final UserService userService;

    public SettingsHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean canHandle(BotSession session, Update update) {
        BotState state = session.getState();
        if (state.name().startsWith("SETTINGS_")) return true;
        
        String text = getText(update);
        if ("⚙️ Настройки".equals(text)) return true;
        
        String data = getData(update);
        if (data != null && (data.equals("MANAGE_SERVICES") || data.equals("ADD_SERVICE") 
            || data.equals("RENAME_SERVICE") || data.equals("UPDATE_SERVICE") || data.equals("DELETE_SERVICE")
            || data.startsWith("REN_SRV_") || data.startsWith("UPD_SRV_") || data.startsWith("DEL_SRV_"))) {
            return true;
        }
        return false;
    }

    @Override
    public SendMessage handle(Update update, BotSession session) {
        Long chatId = getChatId(update);
        String text = getText(update);
        String data = getData(update);

        if ("⚙️ Настройки".equals(text)) {
            SendMessage msg = createMessage(chatId, "Выберите, что хотите изменить:");
            msg.setReplyMarkup(KeyboardUtils.getSettingsKeyboard());
            return msg;
        }

        if (data != null) {
            return handleCallback(chatId, data, session);
        }

        switch (session.getState()) {
            case SETTINGS_ADD_SERVICE_NAME:
                session.setTempServiceName(text);
                session.setState(BotState.SETTINGS_ADD_SERVICE_PERCENTAGE);
                return createMessage(chatId, "Введите процент для услуги '" + text + "':");
            case SETTINGS_ADD_SERVICE_PERCENTAGE:
                try {
                    BigDecimal percent = new BigDecimal(text.replace(",", "."));
                    userService.addServiceCategory(chatId, session.getTempServiceName(), percent);
                    session.setState(BotState.MAIN_MENU);
                    return createMessage(chatId, "✅ Услуга '" + session.getTempServiceName() + "' успешно добавлена!");
                } catch (NumberFormatException e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }
            case SETTINGS_RENAME_SERVICE_NAME:
                userService.renameService(session.getTempServiceId(), text);
                session.setState(BotState.MAIN_MENU);
                return createMessage(chatId, "✅ Услуга переименована в '" + text + "'!");
            case SETTINGS_UPDATE_SERVICE_PERCENTAGE:
                try {
                    BigDecimal percent = new BigDecimal(text.replace(",", "."));
                    userService.updateServicePercentage(session.getTempServiceId(), percent);
                    session.setState(BotState.MAIN_MENU);
                    return createMessage(chatId, "✅ Процент услуги обновлен!");
                } catch (NumberFormatException e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }
            default:
                return null;
        }
    }

    private SendMessage handleCallback(Long chatId, String data, BotSession session) {
        if ("MANAGE_SERVICES".equals(data)) {
            SendMessage msg = createMessage(chatId, "Управление услугами:");
            msg.setReplyMarkup(KeyboardUtils.getManageServicesKeyboard());
            return msg;
        }
        if ("ADD_SERVICE".equals(data)) {
            session.setState(BotState.SETTINGS_ADD_SERVICE_NAME);
            return createMessage(chatId, "Введите название новой услуги:");
        }
        if ("RENAME_SERVICE".equals(data)) {
            java.util.List<com.payroll.bot.entity.ServiceCategory> services = userService.getServiceCategories(chatId);
            if (services.isEmpty()) return createMessage(chatId, "У вас пока нет добавленных услуг.");
            session.setState(BotState.SETTINGS_RENAME_SERVICE_NAME);
            SendMessage msg = createMessage(chatId, "Выберите услугу для переименования:");
            msg.setReplyMarkup(KeyboardUtils.getServiceListKeyboard(services, "REN_SRV_"));
            return msg;
        }
        if ("UPDATE_SERVICE".equals(data)) {
            java.util.List<com.payroll.bot.entity.ServiceCategory> services = userService.getServiceCategories(chatId);
            if (services.isEmpty()) return createMessage(chatId, "У вас пока нет добавленных услуг.");
            SendMessage msg = createMessage(chatId, "Выберите услугу для изменения процента:");
            msg.setReplyMarkup(KeyboardUtils.getServiceListKeyboard(services, "UPD_SRV_"));
            return msg;
        }
        if ("DELETE_SERVICE".equals(data)) {
            java.util.List<com.payroll.bot.entity.ServiceCategory> services = userService.getServiceCategories(chatId);
            if (services.isEmpty()) return createMessage(chatId, "У вас пока нет добавленных услуг.");
            SendMessage msg = createMessage(chatId, "Выберите услугу для удаления:");
            msg.setReplyMarkup(KeyboardUtils.getServiceListKeyboard(services, "DEL_SRV_"));
            return msg;
        }
        if (data.startsWith("REN_SRV_")) {
            Long srvId = Long.parseLong(data.substring("REN_SRV_".length()));
            session.setTempServiceId(srvId);
            session.setState(BotState.SETTINGS_RENAME_SERVICE_NAME);
            return createMessage(chatId, "Введите новое название для этой услуги:");
        }
        if (data.startsWith("UPD_SRV_")) {
            Long srvId = Long.parseLong(data.substring("UPD_SRV_".length()));
            session.setTempServiceId(srvId);
            session.setState(BotState.SETTINGS_UPDATE_SERVICE_PERCENTAGE);
            return createMessage(chatId, "Введите новый процент для этой услуги (например 50):");
        }
        if (data.startsWith("DEL_SRV_")) {
            Long srvId = Long.parseLong(data.substring("DEL_SRV_".length()));
            userService.deleteService(srvId);
            return createMessage(chatId, "✅ Услуга успешно удалена!");
        }
        return null;
    }
}
