package com.payroll.bot.telegram;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.telegram.handler.BotStateHandler;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
public class UpdateHandler {

    private final List<BotStateHandler> handlers;

    public UpdateHandler(List<BotStateHandler> handlers) {
        this.handlers = handlers;
    }

    public SendMessage handleUpdate(Update update, BotSession session) {
        for (BotStateHandler handler : handlers) {
            if (handler.canHandle(session, update)) {
                SendMessage result = handler.handle(update, session);
                if (result != null) {
                    return result;
                }
            }
        }
        
        // Fallback for unknown commands
        SendMessage msg = new SendMessage();
        Long chatId = update.hasMessage() ? update.getMessage().getChatId() : update.getCallbackQuery().getMessage().getChatId();
        msg.setChatId(chatId.toString());
        msg.setText("Неизвестная команда или действие недоступно в данный момент. Нажмите /start");
        return msg;
    }
}
