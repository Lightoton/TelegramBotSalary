package com.payroll.bot.telegram;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollTelegramBotTest {

    @Mock
    private UpdateHandler updateHandler;

    @Mock
    private SessionService sessionService;

    private PayrollTelegramBot bot;

    @BeforeEach
    void setUp() {
        bot = spy(new PayrollTelegramBot("test-token", "test-bot", updateHandler, sessionService));
    }

    @Test
    void testGetBotUsername() {
        assertEquals("test-bot", bot.getBotUsername());
    }

    @Test
    void testOnUpdateReceived_TextMessage_InlineResponse() throws TelegramApiException {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        when(sessionService.getSession(chatId)).thenReturn(session);

        Update update = new Update();
        Message msg = new Message();
        msg.setMessageId(10);
        msg.setChat(new Chat(chatId, "private"));
        msg.setText("Привет");
        update.setMessage(msg);

        SendMessage response = new SendMessage(chatId.toString(), "Ответ");
        when(updateHandler.handleUpdate(update, session)).thenReturn(response);

        Message sentMsg = new Message();
        sentMsg.setMessageId(11);
        doReturn(sentMsg).when(bot).execute(any(SendMessage.class));

        bot.onUpdateReceived(update);

        verify(updateHandler).handleUpdate(update, session);
        verify(sessionService).saveSession(session);
        assertEquals(2, session.getRecentMessageIds().size()); // user msg (10) + bot msg (11)
    }

    @Test
    void testOnUpdateReceived_ReplyKeyboard_AnchorReplaced() throws TelegramApiException {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.setAnchorMessageId(5); // old anchor
        when(sessionService.getSession(chatId)).thenReturn(session);

        Update update = new Update();
        Message msg = new Message();
        msg.setMessageId(20);
        msg.setChat(new Chat(chatId, "private"));
        msg.setText("/start");
        update.setMessage(msg);

        SendMessage response = new SendMessage(chatId.toString(), "Меню");
        response.setReplyMarkup(new ReplyKeyboardMarkup());
        when(updateHandler.handleUpdate(update, session)).thenReturn(response);

        Message sentMsg = new Message();
        sentMsg.setMessageId(21);
        doReturn(sentMsg).when(bot).execute(any(SendMessage.class));
        doReturn(true).when(bot).execute(any(DeleteMessage.class));

        bot.onUpdateReceived(update);

        verify(bot).execute(any(DeleteMessage.class));
        assertEquals(21, session.getAnchorMessageId());
        verify(sessionService).saveSession(session);
    }

    @Test
    void testOnUpdateReceived_CallbackQuery_TrimsHistoryOverThree() throws TelegramApiException {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.getRecentMessageIds().add(1);
        session.getRecentMessageIds().add(2);
        session.getRecentMessageIds().add(3);
        when(sessionService.getSession(chatId)).thenReturn(session);

        Update update = new Update();
        CallbackQuery cb = new CallbackQuery();
        Message cbMsg = new Message();
        cbMsg.setChat(new Chat(chatId, "private"));
        cb.setMessage(cbMsg);
        cb.setData("TEST");
        update.setCallbackQuery(cb);

        SendMessage response = new SendMessage(chatId.toString(), "Инлайн");
        when(updateHandler.handleUpdate(update, session)).thenReturn(response);

        Message sentMsg = new Message();
        sentMsg.setMessageId(4);
        doReturn(sentMsg).when(bot).execute(any(SendMessage.class));
        doReturn(true).when(bot).execute(any(DeleteMessage.class));

        bot.onUpdateReceived(update);

        // Message 1 should be deleted and trimmed
        verify(bot).execute(any(DeleteMessage.class));
        assertEquals(3, session.getRecentMessageIds().size());
        verify(sessionService).saveSession(session);
    }

    @Test
    void testOnUpdateReceived_ExceptionHandler() throws TelegramApiException {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        when(sessionService.getSession(chatId)).thenReturn(session);

        Update update = new Update();
        Message msg = new Message();
        msg.setMessageId(10);
        msg.setChat(new Chat(chatId, "private"));
        msg.setText("Ошибка");
        update.setMessage(msg);

        when(updateHandler.handleUpdate(update, session)).thenThrow(new RuntimeException("Crash"));
        doReturn(new Message()).when(bot).execute(any(SendMessage.class));

        bot.onUpdateReceived(update);

        // Verifies error notification sent to user
        verify(bot).execute(argThat((SendMessage sm) -> sm != null && sm.getText().contains("Произошла ошибка")));
    }
}
