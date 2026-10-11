package com.payroll.bot.service;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.repository.BotSessionRepository;
import com.payroll.bot.repository.BotUserRepository;
import com.payroll.bot.telegram.BotState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private BotSessionRepository sessionRepository;

    @Mock
    private BotUserRepository userRepository;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, userRepository);
    }

    @Test
    void testGetSession_ExistingSession() {
        Long chatId = 123L;
        BotSession existing = new BotSession(chatId);
        existing.setState(BotState.ADD_INCOME_DATE);
        when(sessionRepository.findById(chatId)).thenReturn(Optional.of(existing));

        BotSession result = sessionService.getSession(chatId);
        assertEquals(BotState.ADD_INCOME_DATE, result.getState());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void testGetSession_NewSession_RegisteredUser() {
        Long chatId = 123L;
        when(sessionRepository.findById(chatId)).thenReturn(Optional.empty());
        when(userRepository.existsById(chatId)).thenReturn(true);
        when(sessionRepository.save(any(BotSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BotSession result = sessionService.getSession(chatId);
        assertNotNull(result);
        assertEquals(BotState.MAIN_MENU, result.getState());
        verify(sessionRepository).save(result);
    }

    @Test
    void testGetSession_NewSession_UnregisteredUser() {
        Long chatId = 123L;
        when(sessionRepository.findById(chatId)).thenReturn(Optional.empty());
        when(userRepository.existsById(chatId)).thenReturn(false);
        when(sessionRepository.save(any(BotSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BotSession result = sessionService.getSession(chatId);
        assertNotNull(result);
        assertEquals(BotState.AWAITING_PASSWORD, result.getState());
        verify(sessionRepository).save(result);
    }

    @Test
    void testSaveSession() {
        BotSession session = new BotSession(123L);
        sessionService.saveSession(session);
        verify(sessionRepository).save(session);
    }

    @Test
    void testSetSessionState() {
        Long chatId = 123L;
        BotSession session = new BotSession(chatId);
        when(sessionRepository.findById(chatId)).thenReturn(Optional.of(session));

        sessionService.setSessionState(chatId, BotState.EDIT_TAX);
        assertEquals(BotState.EDIT_TAX, session.getState());
        verify(sessionRepository).save(session);
    }

    @Test
    void testClearSession() {
        Long chatId = 123L;
        sessionService.clearSession(chatId);
        verify(sessionRepository).deleteById(chatId);
    }
}
