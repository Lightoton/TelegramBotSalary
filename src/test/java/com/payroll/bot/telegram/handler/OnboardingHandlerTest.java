package com.payroll.bot.telegram.handler;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.service.MessageService;
import com.payroll.bot.service.UserService;
import com.payroll.bot.telegram.BotState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnboardingHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private MessageService messageService;

    private final String masterPassword = "secret_password";
    private OnboardingHandler onboardingHandler;

    @BeforeEach
    void setUp() {
        onboardingHandler = new OnboardingHandler(userService, messageService, masterPassword);
    }

    private Update createTextUpdate(Long chatId, String text) {
        Update update = new Update();
        Message msg = new Message();
        msg.setChat(new Chat(chatId, "private"));
        msg.setText(text);
        update.setMessage(msg);
        return update;
    }

    private Update createCallbackUpdate(Long chatId, String data) {
        Update update = new Update();
        CallbackQuery cb = new CallbackQuery();
        Message msg = new Message();
        msg.setChat(new Chat(chatId, "private"));
        cb.setMessage(msg);
        cb.setData(data);
        update.setCallbackQuery(cb);
        return update;
    }

    @Test
    void testCanHandle() {
        BotSession session = new BotSession(100L);
        session.setState(BotState.AWAITING_PASSWORD);
        assertTrue(onboardingHandler.canHandle(session, createTextUpdate(100L, "pass")));

        session.setState(BotState.ONBOARDING_ADD_SERVICE_NAME);
        assertTrue(onboardingHandler.canHandle(session, createTextUpdate(100L, "service")));

        session.setState(BotState.MAIN_MENU);
        assertTrue(onboardingHandler.canHandle(session, createCallbackUpdate(100L, "FINISH_ONBOARDING_SERVICES")));
        assertFalse(onboardingHandler.canHandle(session, createTextUpdate(100L, "random")));
    }

    @Test
    void testHandle_PasswordVerification() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.setState(BotState.AWAITING_PASSWORD);

        when(messageService.getMessage("onboarding.wrong_password")).thenReturn("Неверный пароль");
        when(messageService.getMessage("onboarding.welcome")).thenReturn("Добро пожаловать");

        // Wrong password
        SendMessage respWrong = onboardingHandler.handle(createTextUpdate(chatId, "wrong_pass"), session);
        assertEquals(BotState.AWAITING_PASSWORD, session.getState());
        assertEquals("Неверный пароль", respWrong.getText());
        verify(userService, never()).registerUser(any());

        // Correct password
        SendMessage respCorrect = onboardingHandler.handle(createTextUpdate(chatId, masterPassword), session);
        assertEquals(BotState.ONBOARDING_ADD_SERVICE_NAME, session.getState());
        assertEquals("Добро пожаловать", respCorrect.getText());
        verify(userService, times(1)).registerUser(chatId);
    }

    @Test
    void testHandle_AddServicesCycle() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);

        // Step 1: Service Name
        session.setState(BotState.ONBOARDING_ADD_SERVICE_NAME);
        when(messageService.getMessage(eq("onboarding.service.percentage"), eq("Массаж"))).thenReturn("Введите процент для Массаж");

        SendMessage respName = onboardingHandler.handle(createTextUpdate(chatId, "Массаж"), session);
        assertEquals("Массаж", session.getTempServiceName());
        assertEquals(BotState.ONBOARDING_ADD_SERVICE_PERCENTAGE, session.getState());
        assertEquals("Введите процент для Массаж", respName.getText());

        // Step 2: Percentage format error
        when(messageService.getMessage("error.number.format")).thenReturn("Неверный формат числа");
        SendMessage respErr = onboardingHandler.handle(createTextUpdate(chatId, "abc"), session);
        assertEquals("Неверный формат числа", respErr.getText());

        // Step 2: Valid Percentage
        when(messageService.getMessage("onboarding.service.added")).thenReturn("Услуга добавлена");
        SendMessage respValid = onboardingHandler.handle(createTextUpdate(chatId, "45,5"), session);
        verify(userService).addServiceCategory(chatId, "Массаж", new BigDecimal("45.5"));
        assertEquals(BotState.ONBOARDING_ADD_SERVICE_NAME, session.getState());
        assertEquals("Услуга добавлена", respValid.getText());
        assertNotNull(respValid.getReplyMarkup());

        // Finish Services Callback
        when(messageService.getMessage("onboarding.hourly_rate")).thenReturn("Введите ставку в час");
        SendMessage respFinish = onboardingHandler.handle(createCallbackUpdate(chatId, "FINISH_ONBOARDING_SERVICES"), session);
        assertEquals(BotState.ONBOARDING_HOURLY_RATE, session.getState());
        assertEquals("Введите ставку в час", respFinish.getText());
    }

    @Test
    void testHandle_HourlyRateAndMonthlyTax() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);

        // Hourly rate negative
        session.setState(BotState.ONBOARDING_HOURLY_RATE);
        when(messageService.getMessage("error.number.negative")).thenReturn("Число не может быть отрицательным");
        SendMessage respNegHourly = onboardingHandler.handle(createTextUpdate(chatId, "-10"), session);
        assertEquals("Число не может быть отрицательным", respNegHourly.getText());

        // Hourly rate invalid
        when(messageService.getMessage("error.number.format")).thenReturn("Неверный формат числа");
        SendMessage respFormatHourly = onboardingHandler.handle(createTextUpdate(chatId, "xyz"), session);
        assertEquals("Неверный формат числа", respFormatHourly.getText());

        // Hourly rate valid
        when(messageService.getMessage("onboarding.tax")).thenReturn("Введите налог");
        SendMessage respValidHourly = onboardingHandler.handle(createTextUpdate(chatId, "20.00"), session);
        verify(userService).updateHourlyRate(chatId, new BigDecimal("20.00"));
        assertEquals(BotState.ONBOARDING_MONTHLY_TAX, session.getState());
        assertEquals("Введите налог", respValidHourly.getText());

        // Monthly tax negative
        SendMessage respNegTax = onboardingHandler.handle(createTextUpdate(chatId, "-5"), session);
        assertEquals("Число не может быть отрицательным", respNegTax.getText());

        // Monthly tax invalid
        SendMessage respFormatTax = onboardingHandler.handle(createTextUpdate(chatId, "invalid"), session);
        assertEquals("Неверный формат числа", respFormatTax.getText());

        // Monthly tax valid
        when(messageService.getMessage("onboarding.finish")).thenReturn("Онбординг завершен!");
        SendMessage respValidTax = onboardingHandler.handle(createTextUpdate(chatId, "150,00"), session);
        verify(userService).updateMonthlyTax(chatId, new BigDecimal("150.00"));
        assertEquals(BotState.MAIN_MENU, session.getState());
        assertEquals("Онбординг завершен!", respValidTax.getText());
        assertNotNull(respValidTax.getReplyMarkup());
    }

    @Test
    void testHandle_NullText() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.setState(BotState.AWAITING_PASSWORD);

        SendMessage resp = onboardingHandler.handle(createTextUpdate(chatId, null), session);
        assertTrue(resp.getText().contains("отправьте текстовое сообщение или число"));
    }
}
