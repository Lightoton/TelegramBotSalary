package com.payroll.bot.telegram;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.telegram.handler.BotStateHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateHandlerTest {

    @Mock
    private BotStateHandler handler1;

    @Mock
    private BotStateHandler handler2;

    @Test
    void testHandleUpdate_DelegatesToMatchingHandler() {
        UpdateHandler updateHandler = new UpdateHandler(List.of(handler1, handler2));
        BotSession session = new BotSession(123L);

        Update update = new Update();
        Message msg = new Message();
        msg.setChat(new Chat(123L, "private"));
        msg.setText("test");
        update.setMessage(msg);

        SendMessage response = new SendMessage("123", "Response");
        when(handler1.canHandle(session, update)).thenReturn(false);
        when(handler2.canHandle(session, update)).thenReturn(true);
        when(handler2.handle(update, session)).thenReturn(response);

        SendMessage result = updateHandler.handleUpdate(update, session);
        assertEquals("Response", result.getText());
        verify(handler1).canHandle(session, update);
        verify(handler2).canHandle(session, update);
        verify(handler2).handle(update, session);
    }

    @Test
    void testHandleUpdate_FallbackWhenNoHandlerMatches() {
        UpdateHandler updateHandler = new UpdateHandler(List.of(handler1));
        BotSession session = new BotSession(123L);

        Update update = new Update();
        Message msg = new Message();
        msg.setChat(new Chat(123L, "private"));
        msg.setText("unknown");
        update.setMessage(msg);

        when(handler1.canHandle(session, update)).thenReturn(false);

        SendMessage result = updateHandler.handleUpdate(update, session);
        assertNotNull(result);
        assertTrue(result.getText().contains("Неизвестная команда"));
        assertEquals("123", result.getChatId());
    }
}
