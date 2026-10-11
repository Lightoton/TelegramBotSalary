package com.payroll.bot.telegram.handler;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.entity.ServiceCategory;
import com.payroll.bot.entity.TransactionRecord;
import com.payroll.bot.service.CalculationService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncomeHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private CalculationService calculationService;

    private IncomeHandler incomeHandler;

    @BeforeEach
    void setUp() {
        incomeHandler = new IncomeHandler(userService, calculationService);
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
    void testCanHandle_VariousStatesAndTriggers() {
        BotSession session = new BotSession(100L);
        session.setState(BotState.MAIN_MENU);

        Update textIncome = createTextUpdate(100L, "💰 Добавить доход");
        assertTrue(incomeHandler.canHandle(session, textIncome));

        Update textOther = createTextUpdate(100L, "📊 Статистика");
        assertFalse(incomeHandler.canHandle(session, textOther));

        Update editTx = createCallbackUpdate(100L, "EDIT_TX_42");
        assertTrue(incomeHandler.canHandle(session, editTx));

        session.setState(BotState.ADD_INCOME_DATE);
        assertTrue(incomeHandler.canHandle(session, createCallbackUpdate(100L, "DATE_TODAY")));
        assertTrue(incomeHandler.canHandle(session, createCallbackUpdate(100L, "CAL_DAY_2026-10-12")));
        assertTrue(incomeHandler.canHandle(session, createCallbackUpdate(100L, "CAL_PREV_2026-09-01")));

        session.setState(BotState.ADD_INCOME_SERVICE);
        assertTrue(incomeHandler.canHandle(session, createCallbackUpdate(100L, "SRV_DYN_5")));
        assertTrue(incomeHandler.canHandle(session, createCallbackUpdate(100L, "SRV_HOURLY")));

        session.setState(BotState.ADD_INCOME_VALUE);
        assertTrue(incomeHandler.canHandle(session, createTextUpdate(100L, "100")));

        session.setState(BotState.EDIT_TRANSACTION_VALUE);
        assertTrue(incomeHandler.canHandle(session, createTextUpdate(100L, "200")));
    }

    @Test
    void testHandle_AddIncomeButton() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.setState(BotState.MAIN_MENU);

        Update update = createTextUpdate(chatId, "💰 Добавить доход");
        SendMessage response = incomeHandler.handle(update, session);

        assertNotNull(response);
        assertEquals(BotState.ADD_INCOME_DATE, session.getState());
        assertTrue(response.getText().contains("Выберите дату дохода"));
        assertNotNull(response.getReplyMarkup());
    }

    @Test
    void testHandle_EditTransaction_OpenMonth() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        TransactionRecord record = new TransactionRecord(15L, chatId, "LAZER", new BigDecimal("100"), new BigDecimal("40"), LocalDate.of(2026, 10, 1), LocalDateTime.now());

        when(calculationService.getTransactionById(15L)).thenReturn(record);
        when(calculationService.isMonthClosed(chatId, 10, 2026)).thenReturn(false);

        Update update = createCallbackUpdate(chatId, "EDIT_TX_15");
        SendMessage response = incomeHandler.handle(update, session);

        assertNotNull(response);
        assertEquals(15L, session.getTempTransactionId());
        assertEquals(BotState.EDIT_TRANSACTION_VALUE, session.getState());
        assertTrue(response.getText().contains("Введите новую сумму"));
    }

    @Test
    void testHandle_EditTransaction_ClosedMonth() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        TransactionRecord record = new TransactionRecord(15L, chatId, "LAZER", new BigDecimal("100"), new BigDecimal("40"), LocalDate.of(2026, 9, 1), LocalDateTime.now());

        when(calculationService.getTransactionById(15L)).thenReturn(record);
        when(calculationService.isMonthClosed(chatId, 9, 2026)).thenReturn(true);

        Update update = createCallbackUpdate(chatId, "EDIT_TX_15");
        SendMessage response = incomeHandler.handle(update, session);

        assertNotNull(response);
        assertTrue(response.getText().contains("Этот месяц закрыт"));
    }

    @Test
    void testHandle_DateSelection_TodayAndYesterdayAndCalendar() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.setState(BotState.ADD_INCOME_DATE);

        ServiceCategory cat = new ServiceCategory();
        cat.setId(1L);
        cat.setName("Маникюр");
        cat.setPercentage(new BigDecimal("50"));
        when(userService.getServiceCategories(chatId)).thenReturn(List.of(cat));

        // DATE_TODAY
        SendMessage respToday = incomeHandler.handle(createCallbackUpdate(chatId, "DATE_TODAY"), session);
        assertEquals(LocalDate.now(), session.getTempDate());
        assertEquals(BotState.ADD_INCOME_SERVICE, session.getState());
        assertTrue(respToday.getText().contains("Выберите услугу"));

        // DATE_YESTERDAY
        session.setState(BotState.ADD_INCOME_DATE);
        SendMessage respYesterday = incomeHandler.handle(createCallbackUpdate(chatId, "DATE_YESTERDAY"), session);
        assertEquals(LocalDate.now().minusDays(1), session.getTempDate());
        assertEquals(BotState.ADD_INCOME_SERVICE, session.getState());

        // DATE_CALENDAR
        session.setState(BotState.ADD_INCOME_DATE);
        SendMessage respCal = incomeHandler.handle(createCallbackUpdate(chatId, "DATE_CALENDAR"), session);
        assertTrue(respCal.getText().contains("Выберите день"));

        // CAL_DAY_
        session.setState(BotState.ADD_INCOME_DATE);
        SendMessage respDay = incomeHandler.handle(createCallbackUpdate(chatId, "CAL_DAY_2026-10-05"), session);
        assertEquals(LocalDate.of(2026, 10, 5), session.getTempDate());
        assertEquals(BotState.ADD_INCOME_SERVICE, session.getState());

        // CAL_PREV_
        session.setState(BotState.ADD_INCOME_DATE);
        SendMessage respPrev = incomeHandler.handle(createCallbackUpdate(chatId, "CAL_PREV_2026-09-01"), session);
        assertTrue(respPrev.getText().contains("Выберите день"));
    }

    @Test
    void testHandle_ServiceSelection_DynamicAndHourly() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.setState(BotState.ADD_INCOME_SERVICE);

        ServiceCategory cat = new ServiceCategory();
        cat.setId(2L);
        cat.setName("Педикюр");
        cat.setPercentage(new BigDecimal("40"));
        when(userService.getServiceCategories(chatId)).thenReturn(List.of(cat));

        // Dynamic service
        SendMessage respDyn = incomeHandler.handle(createCallbackUpdate(chatId, "SRV_DYN_2"), session);
        assertEquals("Педикюр", session.getTempServiceType());
        assertEquals(BotState.ADD_INCOME_VALUE, session.getState());
        assertTrue(respDyn.getText().contains("Педикюр"));

        // Hourly service
        session.setState(BotState.ADD_INCOME_SERVICE);
        SendMessage respHourly = incomeHandler.handle(createCallbackUpdate(chatId, "SRV_HOURLY"), session);
        assertEquals("HOURLY", session.getTempServiceType());
        assertEquals(BotState.ADD_INCOME_VALUE, session.getState());
        assertTrue(respHourly.getText().contains("HH:mm-HH:mm"));
    }

    @Test
    void testHandle_ValueInput_RegularAndHourlyAndError() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.setState(BotState.ADD_INCOME_VALUE);
        session.setTempDate(LocalDate.of(2026, 10, 11));
        session.setTempServiceType("Педикюр");

        // Null text input
        SendMessage respNull = incomeHandler.handle(createTextUpdate(chatId, null), session);
        assertTrue(respNull.getText().contains("отправьте текстовое значение"));

        // Valid regular service input
        TransactionRecord record = new TransactionRecord(1L, chatId, "Педикюр", new BigDecimal("100"), new BigDecimal("40.00"), session.getTempDate(), LocalDateTime.now());
        when(calculationService.addIncome(eq(chatId), eq(session.getTempDate()), eq("Педикюр"), eq("100"))).thenReturn(record);

        SendMessage respSuccess = incomeHandler.handle(createTextUpdate(chatId, "100"), session);
        assertEquals(BotState.MAIN_MENU, session.getState());
        assertTrue(respSuccess.getText().contains("Доход успешно добавлен"));
        assertTrue(respSuccess.getText().contains("40.00 €"));

        // Valid hourly service input
        session.setState(BotState.ADD_INCOME_VALUE);
        session.setTempServiceType("HOURLY");
        TransactionRecord hourlyRecord = new TransactionRecord(2L, chatId, "HOURLY", new BigDecimal("2.50"), new BigDecimal("50.00"), session.getTempDate(), LocalDateTime.now());
        when(calculationService.addIncome(eq(chatId), eq(session.getTempDate()), eq("HOURLY"), eq("10:00-12:30"))).thenReturn(hourlyRecord);

        SendMessage respHourly = incomeHandler.handle(createTextUpdate(chatId, "10:00-12:30"), session);
        assertEquals(BotState.MAIN_MENU, session.getState());
        assertTrue(respHourly.getText().contains("Отработано часов"));

        // Exception from calculationService
        session.setState(BotState.ADD_INCOME_VALUE);
        when(calculationService.addIncome(anyLong(), any(), anyString(), eq("bad_input"))).thenThrow(new IllegalArgumentException("Неверный формат"));
        SendMessage respErr = incomeHandler.handle(createTextUpdate(chatId, "bad_input"), session);
        assertTrue(respErr.getText().contains("Ошибка: Неверный формат"));
    }

    @Test
    void testHandle_EditTransactionValue_SuccessAndFail() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.setState(BotState.EDIT_TRANSACTION_VALUE);
        session.setTempTransactionId(99L);

        // Null input
        SendMessage respNull = incomeHandler.handle(createTextUpdate(chatId, null), session);
        assertTrue(respNull.getText().contains("числовое значение суммы"));

        // Valid update
        doNothing().when(calculationService).updateTransaction(99L, "150");
        SendMessage respValid = incomeHandler.handle(createTextUpdate(chatId, "150"), session);
        assertEquals(BotState.MAIN_MENU, session.getState());
        assertTrue(respValid.getText().contains("Запись успешно обновлена"));

        // Exception
        session.setState(BotState.EDIT_TRANSACTION_VALUE);
        session.setTempTransactionId(99L);
        doThrow(new NumberFormatException()).when(calculationService).updateTransaction(99L, "abc");
        SendMessage respInvalid = incomeHandler.handle(createTextUpdate(chatId, "abc"), session);
        assertTrue(respInvalid.getText().contains("корректное число"));
    }
}
