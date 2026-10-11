package com.payroll.bot.telegram;

import com.payroll.bot.entity.ServiceCategory;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeyboardUtilsTest {

    @Test
    void testGetMainMenu() {
        ReplyKeyboardMarkup menu = KeyboardUtils.getMainMenu();
        assertNotNull(menu);
        assertEquals(3, menu.getKeyboard().size());
        assertEquals("💰 Добавить доход", menu.getKeyboard().get(0).get(0).getText());
        assertEquals("📊 Статистика", menu.getKeyboard().get(0).get(1).getText());
        assertEquals("🔒 Закрыть месяц", menu.getKeyboard().get(1).get(0).getText());
        assertEquals("📁 История", menu.getKeyboard().get(1).get(1).getText());
        assertEquals("🏠 На жизнь", menu.getKeyboard().get(2).get(0).getText());
        assertEquals("⚙️ Настройки", menu.getKeyboard().get(2).get(1).getText());
    }

    @Test
    void testGetDateSelectionKeyboard() {
        InlineKeyboardMarkup markup = KeyboardUtils.getDateSelectionKeyboard();
        assertNotNull(markup);
        assertEquals(2, markup.getKeyboard().size());
        assertEquals("DATE_TODAY", markup.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("DATE_YESTERDAY", markup.getKeyboard().get(0).get(1).getCallbackData());
        assertEquals("DATE_CALENDAR", markup.getKeyboard().get(1).get(0).getCallbackData());
        assertEquals("CANCEL", markup.getKeyboard().get(1).get(1).getCallbackData());
    }

    @Test
    void testGetServiceSelectionKeyboard() {
        InlineKeyboardMarkup markup = KeyboardUtils.getServiceSelectionKeyboard();
        assertNotNull(markup);
        assertEquals(4, markup.getKeyboard().size());
        assertEquals("SRV_LAZER", markup.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("SRV_MASSAGE", markup.getKeyboard().get(1).get(0).getCallbackData());
        assertEquals("SRV_HOURLY", markup.getKeyboard().get(2).get(0).getCallbackData());
        assertEquals("CANCEL", markup.getKeyboard().get(3).get(0).getCallbackData());
    }

    @Test
    void testGetCalendarKeyboard_WithoutAndWithActiveDates() {
        LocalDate date = LocalDate.of(2026, 10, 1);

        // Without active dates
        InlineKeyboardMarkup normal = KeyboardUtils.getCalendarKeyboard(date, null);
        assertNotNull(normal);
        assertTrue(normal.getKeyboard().size() >= 5);

        // With active dates
        LocalDate activeDay = LocalDate.of(2026, 10, 5);
        InlineKeyboardMarkup withActive = KeyboardUtils.getCalendarKeyboard(date, List.of(activeDay));
        assertNotNull(withActive);
        assertTrue(withActive.getKeyboard().stream()
                .flatMap(List::stream)
                .anyMatch(btn -> btn.getText().contains("• 5")));
    }

    @Test
    void testGetStatisticsDateSelectionKeyboard() {
        InlineKeyboardMarkup markup = KeyboardUtils.getStatisticsDateSelectionKeyboard();
        assertNotNull(markup);
        assertEquals(2, markup.getKeyboard().size());
        assertEquals("STAT_TODAY", markup.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("STAT_YESTERDAY", markup.getKeyboard().get(0).get(1).getCallbackData());
        assertEquals("STAT_CALENDAR", markup.getKeyboard().get(1).get(0).getCallbackData());
    }

    @Test
    void testGetSettingsAndManageServicesKeyboards() {
        InlineKeyboardMarkup settings = KeyboardUtils.getSettingsKeyboard();
        assertNotNull(settings);
        assertEquals(3, settings.getKeyboard().size());
        assertEquals("MANAGE_SERVICES", settings.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("SET_HOURLY", settings.getKeyboard().get(1).get(0).getCallbackData());
        assertEquals("SET_TAX", settings.getKeyboard().get(2).get(0).getCallbackData());

        InlineKeyboardMarkup manage = KeyboardUtils.getManageServicesKeyboard();
        assertNotNull(manage);
        assertEquals(4, manage.getKeyboard().size());
        assertEquals("ADD_SERVICE", manage.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("RENAME_SERVICE", manage.getKeyboard().get(1).get(0).getCallbackData());
        assertEquals("UPDATE_SERVICE", manage.getKeyboard().get(2).get(0).getCallbackData());
        assertEquals("DELETE_SERVICE", manage.getKeyboard().get(3).get(0).getCallbackData());
    }

    @Test
    void testDynamicServiceAndListKeyboards() {
        ServiceCategory svc = new ServiceCategory();
        svc.setId(10L);
        svc.setName("Педикюр");
        svc.setPercentage(new BigDecimal("45"));

        InlineKeyboardMarkup dyn = KeyboardUtils.getDynamicServiceSelectionKeyboard(List.of(svc));
        assertNotNull(dyn);
        assertEquals(2, dyn.getKeyboard().size());
        assertEquals("SRV_DYN_10", dyn.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("SRV_HOURLY", dyn.getKeyboard().get(1).get(0).getCallbackData());

        InlineKeyboardMarkup listMarkup = KeyboardUtils.getServiceListKeyboard(List.of(svc), "DEL_SRV_");
        assertNotNull(listMarkup);
        assertEquals(2, listMarkup.getKeyboard().size());
        assertEquals("DEL_SRV_10", listMarkup.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("CANCEL", listMarkup.getKeyboard().get(1).get(0).getCallbackData());
    }

    @Test
    void testOnboardingFinishKeyboard() {
        InlineKeyboardMarkup markup = KeyboardUtils.getOnboardingFinishKeyboard();
        assertNotNull(markup);
        assertEquals(1, markup.getKeyboard().size());
        assertEquals("FINISH_ONBOARDING_SERVICES", markup.getKeyboard().get(0).get(0).getCallbackData());
    }

    @Test
    void testCloseMonthKeyboards() {
        YearMonth ym = YearMonth.of(2026, 8);
        InlineKeyboardMarkup unclosed = KeyboardUtils.getUnclosedMonthsKeyboard(List.of(ym));
        assertNotNull(unclosed);
        assertEquals(2, unclosed.getKeyboard().size());
        assertEquals("CLOSE_MONTH_SELECT_8_2026", unclosed.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("CANCEL_CLOSE_MONTH", unclosed.getKeyboard().get(1).get(0).getCallbackData());

        InlineKeyboardMarkup confirm = KeyboardUtils.getCloseMonthConfirmationKeyboard(8, 2026);
        assertNotNull(confirm);
        assertEquals(1, confirm.getKeyboard().size());
        assertEquals("CONFIRM_CLOSE_MONTH_8_2026", confirm.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("CANCEL_CLOSE_MONTH", confirm.getKeyboard().get(0).get(1).getCallbackData());
    }

    @Test
    void testBudgetEditKeyboard() {
        InlineKeyboardMarkup markup = KeyboardUtils.getBudgetEditKeyboard();
        assertNotNull(markup);
        assertEquals(1, markup.getKeyboard().size());
        assertEquals(3, markup.getKeyboard().get(0).size());
        assertEquals("EDIT_BUDGET_RENT_BTN", markup.getKeyboard().get(0).get(0).getCallbackData());
        assertEquals("EDIT_BUDGET_FOOD_BTN", markup.getKeyboard().get(0).get(1).getCallbackData());
        assertEquals("EDIT_BUDGET_ADDITIONAL_BTN", markup.getKeyboard().get(0).get(2).getCallbackData());
    }
}
