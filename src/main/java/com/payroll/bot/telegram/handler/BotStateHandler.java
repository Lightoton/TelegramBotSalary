package com.payroll.bot.telegram.handler;

import com.payroll.bot.entity.BotSession;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface BotStateHandler {
    boolean canHandle(BotSession session, Update update);
    SendMessage handle(Update update, BotSession session);
}
