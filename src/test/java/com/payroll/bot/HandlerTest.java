package com.payroll.bot;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.entity.TransactionRecord;
import com.payroll.bot.entity.UserSettings;
import com.payroll.bot.service.CalculationService;
import com.payroll.bot.service.UserService;
import com.payroll.bot.telegram.BotState;
import com.payroll.bot.telegram.handler.MainMenuHandler;
import com.payroll.bot.telegram.handler.SettingsHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private CalculationService calculationService;

    private MainMenuHandler mainMenuHandler;
    private SettingsHandler settingsHandler;

    @BeforeEach
    void setUp() {
        mainMenuHandler = new MainMenuHandler(userService, calculationService);
        settingsHandler = new SettingsHandler(userService);
    }

    private Update createCallbackUpdate(Long chatId, String data) {
        Update update = new Update();
        CallbackQuery cb = new CallbackQuery();
        Message msg = new Message();
        msg.setChat(new org.telegram.telegrambots.meta.api.objects.Chat(chatId, "private"));
        cb.setMessage(msg);
        cb.setData(data);
        update.setCallbackQuery(cb);
        return update;
    }

    private Update createTextUpdate(Long chatId, String text) {
        Update update = new Update();
        Message msg = new Message();
        msg.setChat(new org.telegram.telegrambots.meta.api.objects.Chat(chatId, "private"));
        msg.setText(text);
        update.setMessage(msg);
        return update;
    }

    @Test
    void testDetailedStats_NoExceptionOnDateFormatting() {
        Long chatId = 12345L;
        LocalDate date = LocalDate.of(2026, 10, 11);
        BotSession session = new BotSession(chatId);
        session.setState(BotState.MAIN_MENU);

        TransactionRecord record = new TransactionRecord(1L, chatId, "LAZER", new BigDecimal("100"), new BigDecimal("40.00"), date, java.time.LocalDateTime.now());
        when(calculationService.getDailyTransactions(chatId, date)).thenReturn(List.of(record));
        when(calculationService.isMonthClosed(chatId, 10, 2026)).thenReturn(false);

        Update update = createCallbackUpdate(chatId, "CAL_DAY_" + date);
        assertTrue(mainMenuHandler.canHandle(session, update));

        SendMessage response = mainMenuHandler.handle(update, session);
        assertNotNull(response);
        assertTrue(response.getText().contains("Транзакции за 2026-10-11"));
        assertTrue(response.getText().contains("40.00 €"));
    }

    @Test
    void testCloseMonth_SelectionAndConfirmation() {
        Long chatId = 12345L;
        BotSession session = new BotSession(chatId);

        Update selectUpdate = createCallbackUpdate(chatId, "CLOSE_MONTH_SELECT_10_2026");
        assertTrue(mainMenuHandler.canHandle(session, selectUpdate));
        SendMessage selectMsg = mainMenuHandler.handle(selectUpdate, session);
        assertNotNull(selectMsg);
        assertTrue(selectMsg.getText().contains("Вы уверены, что хотите закрыть"));

        Update confirmUpdate = createCallbackUpdate(chatId, "CONFIRM_CLOSE_MONTH_10_2026");
        assertTrue(mainMenuHandler.canHandle(session, confirmUpdate));
        SendMessage confirmMsg = mainMenuHandler.handle(confirmUpdate, session);
        assertNotNull(confirmMsg);
        assertTrue(confirmMsg.getText().contains("успешно закрыт"));
        verify(calculationService, times(1)).closeMonth(chatId, 10, 2026);
    }

    @Test
    void testSettings_SetHourlyAndTax() {
        Long chatId = 12345L;
        BotSession session = new BotSession(chatId);

        Update hourlyCb = createCallbackUpdate(chatId, "SET_HOURLY");
        assertTrue(settingsHandler.canHandle(session, hourlyCb));
        SendMessage msg = settingsHandler.handle(hourlyCb, session);
        assertEquals(BotState.EDIT_HOURLY_RATE, session.getState());

        Update textUpdate = createTextUpdate(chatId, "25.50");
        assertTrue(settingsHandler.canHandle(session, textUpdate));
        SendMessage resultMsg = settingsHandler.handle(textUpdate, session);
        assertEquals(BotState.MAIN_MENU, session.getState());
        assertTrue(resultMsg.getText().contains("Почасовая ставка успешно обновлена"));
        verify(userService, times(1)).updateHourlyRate(chatId, new BigDecimal("25.50"));
    }

    @Test
    void testHistoryButton_ShowsClosedMonths() {
        Long chatId = 12345L;
        BotSession session = new BotSession(chatId);

        com.payroll.bot.entity.MonthlyReport report = new com.payroll.bot.entity.MonthlyReport(1L, chatId, 9, 2026, new BigDecimal("1500.00"), java.time.LocalDateTime.now());
        when(calculationService.getArchivedMonths(chatId)).thenReturn(List.of(report));

        Update update = createTextUpdate(chatId, "📁 История");
        assertTrue(mainMenuHandler.canHandle(session, update));
        SendMessage response = mainMenuHandler.handle(update, session);
        assertNotNull(response);
        assertTrue(response.getText().contains("Архив закрытых месяцев"));
        assertNotNull(response.getReplyMarkup());
    }

    @Test
    void testStatisticsButton_IncludesDetailedStatsInlineButton() {
        Long chatId = 12345L;
        BotSession session = new BotSession(chatId);

        when(calculationService.getMonthlyTotal(eq(chatId), anyInt(), anyInt())).thenReturn(new BigDecimal("2500.00"));
        UserSettings settings = new UserSettings();
        settings.setMonthlyTax(new BigDecimal("200.00"));
        when(userService.getUserSettings(chatId)).thenReturn(settings);

        Update update = createTextUpdate(chatId, "📊 Статистика");
        assertTrue(mainMenuHandler.canHandle(session, update));
        SendMessage response = mainMenuHandler.handle(update, session);
        assertNotNull(response);
        assertTrue(response.getText().contains("Статистика за текущий месяц"));
        assertNotNull(response.getReplyMarkup());
        assertTrue(response.getReplyMarkup() instanceof org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup);
    }
}
