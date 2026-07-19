package com.payroll.bot.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import com.payroll.bot.service.SessionService;
import com.payroll.bot.telegram.BotState;
import com.payroll.bot.telegram.KeyboardUtils;

@Component
public class PayrollTelegramBot extends TelegramLongPollingBot {

    private final String botName;
    private final UpdateHandler updateHandler;
    private final SessionService sessionService;

    public PayrollTelegramBot(@Value("${telegram.bot.token}") String botToken,
                              @Value("${telegram.bot.name}") String botName,
                              UpdateHandler updateHandler,
                              SessionService sessionService) {
        super(botToken);
        this.botName = botName;
        this.updateHandler = updateHandler;
        this.sessionService = sessionService;
    }

    @Override
    public String getBotUsername() {
        return botName;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            Long chatId = null;
            if (update.hasMessage()) {
                chatId = update.getMessage().getChatId();
            } else if (update.hasCallbackQuery()) {
                chatId = update.getCallbackQuery().getMessage().getChatId();
            }

            if (chatId != null) {
                UserSession session = sessionService.getSession(chatId);
                
                // Track user's message
                if (update.hasMessage()) {
                    session.getRecentMessageIds().add(update.getMessage().getMessageId());
                }
                
                SendMessage response = updateHandler.handleUpdate(update);
                if (response != null) {
                    Message sentMessage = execute(response);
                    
                    if (response.getReplyMarkup() instanceof org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup) {
                        // Delete previous anchor if exists
                        if (session.getAnchorMessageId() != null) {
                            try {
                                execute(new DeleteMessage(chatId.toString(), session.getAnchorMessageId()));
                            } catch (Exception e) {}
                        }
                        session.setAnchorMessageId(sentMessage.getMessageId());
                    } else {
                        session.getRecentMessageIds().add(sentMessage.getMessageId());
                    }
                }

                // Cleanup oldest messages if we have more than 3
                while (session.getRecentMessageIds().size() > 3) {
                    Integer oldMsgId = session.getRecentMessageIds().remove(0);
                    try {
                        execute(new DeleteMessage(chatId.toString(), oldMsgId));
                    } catch (Exception e) {
                        // ignore if already deleted or unavailable
                    }
                }
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            if (update.hasMessage() && update.getMessage().hasText()) {
                try {
                    execute(new SendMessage(update.getMessage().getChatId().toString(), "Произошла ошибка при обработке запроса."));
                } catch (TelegramApiException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}
