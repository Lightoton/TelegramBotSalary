package com.payroll.bot.telegram.handler;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.entity.ServiceCategory;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsHandlerTest {

    @Mock
    private UserService userService;

    private SettingsHandler settingsHandler;

    @BeforeEach
    void setUp() {
        settingsHandler = new SettingsHandler(userService);
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

        assertTrue(settingsHandler.canHandle(session, createTextUpdate(100L, "⚙️ Настройки")));
        assertFalse(settingsHandler.canHandle(session, createTextUpdate(100L, "Другое")));

        assertTrue(settingsHandler.canHandle(session, createCallbackUpdate(100L, "MANAGE_SERVICES")));
        assertTrue(settingsHandler.canHandle(session, createCallbackUpdate(100L, "ADD_SERVICE")));
        assertTrue(settingsHandler.canHandle(session, createCallbackUpdate(100L, "REN_SRV_1")));
        assertTrue(settingsHandler.canHandle(session, createCallbackUpdate(100L, "UPD_SRV_1")));
        assertTrue(settingsHandler.canHandle(session, createCallbackUpdate(100L, "DEL_SRV_1")));
    }

    @Test
    void testHandle_OpenSettingsMenu() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);

        SendMessage resp = settingsHandler.handle(createTextUpdate(chatId, "⚙️ Настройки"), session);
        assertNotNull(resp);
        assertTrue(resp.getText().contains("Выберите, что хотите изменить"));
        assertNotNull(resp.getReplyMarkup());
    }

    @Test
    void testHandle_ManageServicesCallbacks() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);

        // MANAGE_SERVICES
        SendMessage respManage = settingsHandler.handle(createCallbackUpdate(chatId, "MANAGE_SERVICES"), session);
        assertTrue(respManage.getText().contains("Управление услугами"));

        // ADD_SERVICE
        SendMessage respAdd = settingsHandler.handle(createCallbackUpdate(chatId, "ADD_SERVICE"), session);
        assertEquals(BotState.SETTINGS_ADD_SERVICE_NAME, session.getState());
        assertTrue(respAdd.getText().contains("Введите название"));

        // RENAME_SERVICE when empty vs present
        when(userService.getServiceCategories(chatId)).thenReturn(Collections.emptyList());
        SendMessage respEmpty = settingsHandler.handle(createCallbackUpdate(chatId, "RENAME_SERVICE"), session);
        assertTrue(respEmpty.getText().contains("нет добавленных услуг"));

        ServiceCategory svc = new ServiceCategory();
        svc.setId(5L);
        svc.setName("Стрижка");
        when(userService.getServiceCategories(chatId)).thenReturn(List.of(svc));

        SendMessage respRenameList = settingsHandler.handle(createCallbackUpdate(chatId, "RENAME_SERVICE"), session);
        assertTrue(respRenameList.getText().contains("Выберите услугу для переименования"));

        SendMessage respUpdList = settingsHandler.handle(createCallbackUpdate(chatId, "UPDATE_SERVICE"), session);
        assertTrue(respUpdList.getText().contains("Выберите услугу для изменения процента"));

        SendMessage respDelList = settingsHandler.handle(createCallbackUpdate(chatId, "DELETE_SERVICE"), session);
        assertTrue(respDelList.getText().contains("Выберите услугу для удаления"));

        // Select specific item to rename
        SendMessage respRenItem = settingsHandler.handle(createCallbackUpdate(chatId, "REN_SRV_5"), session);
        assertEquals(5L, session.getTempServiceId());
        assertEquals(BotState.SETTINGS_RENAME_SERVICE_NAME, session.getState());

        // Select specific item to update percentage
        SendMessage respUpdItem = settingsHandler.handle(createCallbackUpdate(chatId, "UPD_SRV_5"), session);
        assertEquals(5L, session.getTempServiceId());
        assertEquals(BotState.SETTINGS_UPDATE_SERVICE_PERCENTAGE, session.getState());

        // Delete item
        SendMessage respDelItem = settingsHandler.handle(createCallbackUpdate(chatId, "DEL_SRV_5"), session);
        verify(userService).deleteService(5L);
        assertTrue(respDelItem.getText().contains("Услуга успешно удалена"));
    }

    @Test
    void testHandle_AddServiceInputs() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);

        session.setState(BotState.SETTINGS_ADD_SERVICE_NAME);
        SendMessage respName = settingsHandler.handle(createTextUpdate(chatId, "Брови"), session);
        assertEquals("Брови", session.getTempServiceName());
        assertEquals(BotState.SETTINGS_ADD_SERVICE_PERCENTAGE, session.getState());

        // Invalid percent
        SendMessage respErr = settingsHandler.handle(createTextUpdate(chatId, "bad"), session);
        assertTrue(respErr.getText().contains("введите число"));

        // Valid percent
        SendMessage respSuccess = settingsHandler.handle(createTextUpdate(chatId, "30,0"), session);
        verify(userService).addServiceCategory(chatId, "Брови", new BigDecimal("30.0"));
        assertEquals(BotState.MAIN_MENU, session.getState());
        assertTrue(respSuccess.getText().contains("успешно добавлена"));
    }

    @Test
    void testHandle_RenameAndUpdatePercentageInputs() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.setTempServiceId(7L);

        // Rename
        session.setState(BotState.SETTINGS_RENAME_SERVICE_NAME);
        SendMessage respRen = settingsHandler.handle(createTextUpdate(chatId, "Новое имя"), session);
        verify(userService).renameService(7L, "Новое имя");
        assertEquals(BotState.MAIN_MENU, session.getState());
        assertTrue(respRen.getText().contains("переименована"));

        // Update percentage invalid & valid
        session.setState(BotState.SETTINGS_UPDATE_SERVICE_PERCENTAGE);
        SendMessage respErr = settingsHandler.handle(createTextUpdate(chatId, "abc"), session);
        assertTrue(respErr.getText().contains("введите число"));

        SendMessage respOk = settingsHandler.handle(createTextUpdate(chatId, "60"), session);
        verify(userService).updateServicePercentage(7L, new BigDecimal("60"));
        assertEquals(BotState.MAIN_MENU, session.getState());
        assertTrue(respOk.getText().contains("обновлен"));
    }

    @Test
    void testHandle_HourlyRateAndTaxInputs() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);

        // Hourly rate
        session.setState(BotState.EDIT_HOURLY_RATE);
        SendMessage respNegHourly = settingsHandler.handle(createTextUpdate(chatId, "-10"), session);
        assertTrue(respNegHourly.getText().contains("не может быть отрицательным"));

        SendMessage respNanHourly = settingsHandler.handle(createTextUpdate(chatId, "bad"), session);
        assertTrue(respNanHourly.getText().contains("корректное число"));

        SendMessage respOkHourly = settingsHandler.handle(createTextUpdate(chatId, "25"), session);
        verify(userService).updateHourlyRate(chatId, new BigDecimal("25"));
        assertEquals(BotState.MAIN_MENU, session.getState());

        // Tax
        session.setState(BotState.EDIT_TAX);
        SendMessage respNegTax = settingsHandler.handle(createTextUpdate(chatId, "-5"), session);
        assertTrue(respNegTax.getText().contains("не может быть отрицательным"));

        SendMessage respNanTax = settingsHandler.handle(createTextUpdate(chatId, "bad"), session);
        assertTrue(respNanTax.getText().contains("корректное число"));

        SendMessage respOkTax = settingsHandler.handle(createTextUpdate(chatId, "150"), session);
        verify(userService).updateMonthlyTax(chatId, new BigDecimal("150"));
        assertEquals(BotState.MAIN_MENU, session.getState());
    }

    @Test
    void testHandle_NullTextInput() {
        Long chatId = 100L;
        BotSession session = new BotSession(chatId);
        session.setState(BotState.EDIT_HOURLY_RATE);

        SendMessage resp = settingsHandler.handle(createTextUpdate(chatId, null), session);
        assertTrue(resp.getText().contains("отправьте текстовое значение или число"));
    }
}
