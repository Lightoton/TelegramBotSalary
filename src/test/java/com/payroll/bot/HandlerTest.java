package com.payroll.bot;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.entity.MonthlyReport;
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
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
        msg.setChat(new Chat(chatId, "private"));
        cb.setMessage(msg);
        cb.setData(data);
        update.setCallbackQuery(cb);
        return update;
    }

    private Update createTextUpdate(Long chatId, String text) {
        Update update = new Update();
        Message msg = new Message();
        msg.setChat(new Chat(chatId, "private"));
        msg.setText(text);
        update.setMessage(msg);
        return update;
    }

    @Test
    void testStartAndMenuCommands() {
        Long chatId = 12345L;
        BotSession session = new BotSession(chatId);

        Update startUpdate = createTextUpdate(chatId, "/start");
        assertTrue(mainMenuHandler.canHandle(session, startUpdate));
        SendMessage startMsg = mainMenuHandler.handle(startUpdate, session);
        assertEquals(BotState.MAIN_MENU, session.getState());
        assertTrue(startMsg.getText().contains("Главное меню открыто"));

        Update menuUpdate = createTextUpdate(chatId, "/menu");
        assertTrue(mainMenuHandler.canHandle(session, menuUpdate));
        SendMessage menuMsg = mainMenuHandler.handle(menuUpdate, session);
        assertEquals(BotState.MAIN_MENU, session.getState());
        assertTrue(menuMsg.getText().contains("Главное меню открыто"));
    }

    @Test
    void testCancelAndIgnoreCallbacks() {
        Long chatId = 12345L;
        BotSession session = new BotSession(chatId);
        session.setState(BotState.ADD_INCOME_DATE);

        Update cancelUpdate = createCallbackUpdate(chatId, "CANCEL");
        assertTrue(mainMenuHandler.canHandle(session, cancelUpdate));
        SendMessage cancelMsg = mainMenuHandler.handle(cancelUpdate, session);
        assertEquals(BotState.MAIN_MENU, session.getState());
        assertTrue(cancelMsg.getText().contains("Отменено"));

        Update ignoreUpdate = createCallbackUpdate(chatId, "IGNORE");
        assertTrue(mainMenuHandler.canHandle(session, ignoreUpdate));
        SendMessage ignoreMsg = mainMenuHandler.handle(ignoreUpdate, session);
        assertTrue(ignoreMsg.getText().contains("недоступно"));
    }

    @Test
    void testDetailedStats_NoExceptionOnDateFormatting() {
        Long chatId = 12345L;
        LocalDate date = LocalDate.of(2026, 10, 11);
        BotSession session = new BotSession(chatId);
        session.setState(BotState.MAIN_MENU);

        TransactionRecord record = new TransactionRecord(1L, chatId, "LAZER", new BigDecimal("100"), new BigDecimal("40.00"), date, LocalDateTime.now());
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
    void testDetailedStats_ClosedMonthAndEmptyList() {
        Long chatId = 12345L;
        LocalDate date = LocalDate.of(2026, 9, 15);
        BotSession session = new BotSession(chatId);
        session.setState(BotState.MAIN_MENU);

        // Empty list
        when(calculationService.getDailyTransactions(chatId, date)).thenReturn(Collections.emptyList());
        Update emptyUpdate = createCallbackUpdate(chatId, "CAL_DAY_" + date);
        SendMessage emptyResp = mainMenuHandler.handle(emptyUpdate, session);
        assertTrue(emptyResp.getText().contains("Нет записей за 2026-09-15"));

        // Closed month
        TransactionRecord record = new TransactionRecord(1L, chatId, "LAZER", new BigDecimal("100"), new BigDecimal("40.00"), date, LocalDateTime.now());
        when(calculationService.getDailyTransactions(chatId, date)).thenReturn(List.of(record));
        when(calculationService.isMonthClosed(chatId, 9, 2026)).thenReturn(true);

        SendMessage closedResp = mainMenuHandler.handle(emptyUpdate, session);
        assertTrue(closedResp.getText().contains("Транзакции за 2026-09-15"));
    }

    @Test
    void testStatisticsDetailed_OptionsAndCalendar() {
        Long chatId = 12345L;
        BotSession session = new BotSession(chatId);
        session.setState(BotState.MAIN_MENU);

        // Detailed Stats text
        Update detailedText = createTextUpdate(chatId, "📄 Подробная статистика");
        assertTrue(mainMenuHandler.canHandle(session, detailedText));
        SendMessage detailedResp = mainMenuHandler.handle(detailedText, session);
        assertTrue(detailedResp.getText().contains("За какой день"));

        // STAT_MENU callback
        Update statMenuCb = createCallbackUpdate(chatId, "STAT_MENU");
        assertTrue(mainMenuHandler.canHandle(session, statMenuCb));
        SendMessage statMenuResp = mainMenuHandler.handle(statMenuCb, session);
        assertTrue(statMenuResp.getText().contains("За какой день"));

        // STAT_TODAY
        Update todayCb = createCallbackUpdate(chatId, "STAT_TODAY");
        assertTrue(mainMenuHandler.canHandle(session, todayCb));
        when(calculationService.getDailyTransactions(chatId, LocalDate.now())).thenReturn(Collections.emptyList());
        SendMessage todayResp = mainMenuHandler.handle(todayCb, session);
        assertTrue(todayResp.getText().contains("Нет записей"));

        // STAT_YESTERDAY
        Update yestCb = createCallbackUpdate(chatId, "STAT_YESTERDAY");
        assertTrue(mainMenuHandler.canHandle(session, yestCb));
        when(calculationService.getDailyTransactions(chatId, LocalDate.now().minusDays(1))).thenReturn(Collections.emptyList());
        SendMessage yestResp = mainMenuHandler.handle(yestCb, session);
        assertTrue(yestResp.getText().contains("Нет записей"));

        // STAT_CALENDAR
        Update calCb = createCallbackUpdate(chatId, "STAT_CALENDAR");
        assertTrue(mainMenuHandler.canHandle(session, calCb));
        when(calculationService.getTransactionsByMonth(eq(chatId), anyInt(), anyInt())).thenReturn(Collections.emptyList());
        SendMessage calResp = mainMenuHandler.handle(calCb, session);
        assertTrue(calResp.getText().contains("Выберите день"));

        // CAL_PREV_ navigation
        Update navCb = createCallbackUpdate(chatId, "CAL_PREV_2026-09-01");
        assertTrue(mainMenuHandler.canHandle(session, navCb));
        SendMessage navResp = mainMenuHandler.handle(navCb, session);
        assertTrue(navResp.getText().contains("Выберите день"));
    }

    @Test
    void testCloseMonth_SelectionAndConfirmationAndCancel() {
        Long chatId = 12345L;
        BotSession session = new BotSession(chatId);

        // Unclosed months empty
        when(calculationService.getUnclosedMonths(chatId)).thenReturn(Collections.emptyList());
        Update closeBtn = createTextUpdate(chatId, "🔒 Закрыть месяц");
        assertTrue(mainMenuHandler.canHandle(session, closeBtn));
        SendMessage emptyMsg = mainMenuHandler.handle(closeBtn, session);
        assertTrue(emptyMsg.getText().contains("нет открытых месяцев"));

        // Unclosed months list
        when(calculationService.getUnclosedMonths(chatId)).thenReturn(List.of(YearMonth.of(2026, 10)));
        SendMessage listMsg = mainMenuHandler.handle(closeBtn, session);
        assertTrue(listMsg.getText().contains("Выберите месяц для закрытия"));

        // Select month
        Update selectUpdate = createCallbackUpdate(chatId, "CLOSE_MONTH_SELECT_10_2026");
        assertTrue(mainMenuHandler.canHandle(session, selectUpdate));
        SendMessage selectMsg = mainMenuHandler.handle(selectUpdate, session);
        assertNotNull(selectMsg);
        assertTrue(selectMsg.getText().contains("Вы уверены, что хотите закрыть"));

        // Confirm month success
        Update confirmUpdate = createCallbackUpdate(chatId, "CONFIRM_CLOSE_MONTH_10_2026");
        assertTrue(mainMenuHandler.canHandle(session, confirmUpdate));
        SendMessage confirmMsg = mainMenuHandler.handle(confirmUpdate, session);
        assertNotNull(confirmMsg);
        assertTrue(confirmMsg.getText().contains("успешно закрыт"));
        verify(calculationService, times(1)).closeMonth(chatId, 10, 2026);

        // Confirm month exception
        doThrow(new IllegalStateException("Ошибка закрытия")).when(calculationService).closeMonth(chatId, 9, 2026);
        Update confirmErrUpdate = createCallbackUpdate(chatId, "CONFIRM_CLOSE_MONTH_9_2026");
        SendMessage confirmErrMsg = mainMenuHandler.handle(confirmErrUpdate, session);
        assertTrue(confirmErrMsg.getText().contains("Ошибка закрытия"));

        // Cancel close month
        Update cancelClose = createCallbackUpdate(chatId, "CANCEL_CLOSE_MONTH");
        assertTrue(mainMenuHandler.canHandle(session, cancelClose));
        SendMessage cancelCloseMsg = mainMenuHandler.handle(cancelClose, session);
        assertTrue(cancelCloseMsg.getText().contains("Действие отменено"));
    }

    @Test
    void testHistoryButton_ShowsClosedMonthsAndDetails() {
        Long chatId = 12345L;
        BotSession session = new BotSession(chatId);

        // Empty history
        when(calculationService.getArchivedMonths(chatId)).thenReturn(Collections.emptyList());
        Update historyBtn = createTextUpdate(chatId, "📁 История");
        assertTrue(mainMenuHandler.canHandle(session, historyBtn));
        SendMessage emptyHist = mainMenuHandler.handle(historyBtn, session);
        assertTrue(emptyHist.getText().contains("нет закрытых месяцев"));

        // History with report
        MonthlyReport report = new MonthlyReport(1L, chatId, 9, 2026, new BigDecimal("1500.00"), LocalDateTime.now());
        when(calculationService.getArchivedMonths(chatId)).thenReturn(List.of(report));

        SendMessage response = mainMenuHandler.handle(historyBtn, session);
        assertNotNull(response);
        assertTrue(response.getText().contains("Архив закрытых месяцев"));

        // HISTORY_9_2026 callback
        Update histCb = createCallbackUpdate(chatId, "HISTORY_9_2026");
        assertTrue(mainMenuHandler.canHandle(session, histCb));
        SendMessage histResp = mainMenuHandler.handle(histCb, session);
        assertTrue(histResp.getText().contains("Архив 9/2026"));
        assertTrue(histResp.getText().contains("1500.00 €"));

        // HISTORY_DETAIL_9_2026 with empty txs
        when(calculationService.getTransactionsByMonth(chatId, 9, 2026)).thenReturn(Collections.emptyList());
        Update detailEmptyCb = createCallbackUpdate(chatId, "HISTORY_DETAIL_9_2026");
        assertTrue(mainMenuHandler.canHandle(session, detailEmptyCb));
        SendMessage detailEmptyResp = mainMenuHandler.handle(detailEmptyCb, session);
        assertTrue(detailEmptyResp.getText().contains("Транзакции не найдены"));

        // HISTORY_DETAIL_9_2026 with txs
        TransactionRecord tx = new TransactionRecord(1L, chatId, "LAZER", new BigDecimal("100"), new BigDecimal("40.00"), LocalDate.of(2026, 9, 10), LocalDateTime.now());
        when(calculationService.getTransactionsByMonth(chatId, 9, 2026)).thenReturn(List.of(tx));
        SendMessage detailResp = mainMenuHandler.handle(detailEmptyCb, session);
        assertTrue(detailResp.getText().contains("Детализация за 9/2026"));
        assertTrue(detailResp.getText().contains("LAZER"));
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
