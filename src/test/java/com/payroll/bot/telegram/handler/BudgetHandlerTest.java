package com.payroll.bot.telegram.handler;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.entity.UserSettings;
import com.payroll.bot.service.BudgetService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private BudgetService budgetService;

    private BudgetHandler budgetHandler;

    @BeforeEach
    void setUp() {
        budgetHandler = new BudgetHandler(userService, budgetService);
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
        session.setState(BotState.MAIN_MENU);

        assertTrue(budgetHandler.canHandle(session, createTextUpdate(100L, "🏠 На жизнь")));
        assertFalse(budgetHandler.canHandle(session, createTextUpdate(100L, "💰 Доход")));

        assertTrue(budgetHandler.canHandle(session, createCallbackUpdate(100L, "EDIT_BUDGET_RENT_BTN")));

        session.setState(BotState.BUDGET_SETUP_RENT);
        assertTrue(budgetHandler.canHandle(session, createTextUpdate(100L, "500")));

        session.setState(BotState.EDIT_BUDGET_FOOD);
        assertTrue(budgetHandler.canHandle(session, createTextUpdate(100L, "300")));
    }

    @Test
    void testHandle_BudgetButton_UnconfiguredVsConfigured() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.setState(BotState.MAIN_MENU);

        UserSettings unconfigured = new UserSettings();
        unconfigured.setRentLimit(new BigDecimal("-1"));
        when(userService.getUserSettings(chatId)).thenReturn(unconfigured);

        // When unconfigured -> enters BUDGET_SETUP_RENT
        SendMessage respUnconfigured = budgetHandler.handle(createTextUpdate(chatId, "🏠 На жизнь"), session);
        assertEquals(BotState.BUDGET_SETUP_RENT, session.getState());
        assertTrue(respUnconfigured.getText().contains("Какая у вас аренда"));

        // When configured -> returns report
        UserSettings configured = new UserSettings();
        configured.setRentLimit(new BigDecimal("500"));
        when(userService.getUserSettings(chatId)).thenReturn(configured);
        when(budgetService.generateBudgetReport(eq(chatId), anyInt(), anyInt())).thenReturn("Отчёт по бюджету");

        SendMessage respConfigured = budgetHandler.handle(createTextUpdate(chatId, "🏠 На жизнь"), session);
        assertTrue(respConfigured.getText().contains("Отчёт по бюджету"));
        assertNotNull(respConfigured.getReplyMarkup());
    }

    @Test
    void testHandle_EditBudgetCallbacks() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);

        budgetHandler.handle(createCallbackUpdate(chatId, "EDIT_BUDGET_RENT_BTN"), session);
        assertEquals(BotState.EDIT_BUDGET_RENT, session.getState());

        budgetHandler.handle(createCallbackUpdate(chatId, "EDIT_BUDGET_FOOD_BTN"), session);
        assertEquals(BotState.EDIT_BUDGET_FOOD, session.getState());

        budgetHandler.handle(createCallbackUpdate(chatId, "EDIT_BUDGET_ADDITIONAL_BTN"), session);
        assertEquals(BotState.EDIT_BUDGET_ADDITIONAL, session.getState());

        assertNull(budgetHandler.handle(createCallbackUpdate(chatId, "OTHER_DATA"), session));
    }

    @Test
    void testHandle_StepByStepSetup() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);

        // Step 1: Rent
        session.setState(BotState.BUDGET_SETUP_RENT);
        SendMessage respRent = budgetHandler.handle(createTextUpdate(chatId, "600,50"), session);
        verify(userService).updateRentLimit(chatId, new BigDecimal("600.50"));
        assertEquals(BotState.BUDGET_SETUP_FOOD, session.getState());
        assertTrue(respRent.getText().contains("тратите на продукты"));

        // Step 2: Food
        session.setState(BotState.BUDGET_SETUP_FOOD);
        SendMessage respFood = budgetHandler.handle(createTextUpdate(chatId, "350"), session);
        verify(userService).updateFoodLimit(chatId, new BigDecimal("350"));
        assertEquals(BotState.BUDGET_SETUP_ADDITIONAL, session.getState());
        assertTrue(respFood.getText().contains("дополнительные траты"));

        // Step 3: Additional
        session.setState(BotState.BUDGET_SETUP_ADDITIONAL);
        when(budgetService.generateBudgetReport(eq(chatId), anyInt(), anyInt())).thenReturn("Итоговый отчет");
        SendMessage respAdd = budgetHandler.handle(createTextUpdate(chatId, "150"), session);
        verify(userService).updateAdditionalLimit(chatId, new BigDecimal("150"));
        assertEquals(BotState.MAIN_MENU, session.getState());
        assertTrue(respAdd.getText().contains("Итоговый отчет"));
    }

    @Test
    void testHandle_DirectEdits() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        when(budgetService.generateBudgetReport(eq(chatId), anyInt(), anyInt())).thenReturn("Отчет");

        session.setState(BotState.EDIT_BUDGET_RENT);
        budgetHandler.handle(createTextUpdate(chatId, "700"), session);
        verify(userService).updateRentLimit(chatId, new BigDecimal("700"));
        assertEquals(BotState.MAIN_MENU, session.getState());

        session.setState(BotState.EDIT_BUDGET_FOOD);
        budgetHandler.handle(createTextUpdate(chatId, "400"), session);
        verify(userService).updateFoodLimit(chatId, new BigDecimal("400"));
        assertEquals(BotState.MAIN_MENU, session.getState());

        session.setState(BotState.EDIT_BUDGET_ADDITIONAL);
        budgetHandler.handle(createTextUpdate(chatId, "200"), session);
        verify(userService).updateAdditionalLimit(chatId, new BigDecimal("200"));
        assertEquals(BotState.MAIN_MENU, session.getState());
    }

    @Test
    void testHandle_ValidationErrors() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.setState(BotState.BUDGET_SETUP_RENT);

        // Null text
        SendMessage respNull = budgetHandler.handle(createTextUpdate(chatId, null), session);
        assertTrue(respNull.getText().contains("введите числовое значение"));

        // Negative number
        SendMessage respNeg = budgetHandler.handle(createTextUpdate(chatId, "-50"), session);
        assertTrue(respNeg.getText().contains("не может быть отрицательным"));

        // Invalid format
        SendMessage respNaN = budgetHandler.handle(createTextUpdate(chatId, "abc"), session);
        assertTrue(respNaN.getText().contains("введите число"));
    }
}
