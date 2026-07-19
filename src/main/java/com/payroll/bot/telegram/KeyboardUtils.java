package com.payroll.bot.telegram;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class KeyboardUtils {

    public static ReplyKeyboardMarkup getMainMenu() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("💰 Добавить доход"));
        row1.add(new KeyboardButton("📊 Статистика"));
        
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("🔒 Закрыть месяц"));
        row2.add(new KeyboardButton("📁 История"));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("🏠 На жизнь"));
        row3.add(new KeyboardButton("⚙️ Настройки"));
        
        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    public static InlineKeyboardMarkup getDateSelectionKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineButton("Сегодня", "DATE_TODAY"));
        row1.add(createInlineButton("Вчера", "DATE_YESTERDAY"));
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineButton("📅 Выбрать день", "DATE_CALENDAR"));
        row2.add(createInlineButton("Отмена", "CANCEL"));

        rows.add(row1);
        rows.add(row2);
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup getServiceSelectionKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineButton("Лазерная эпиляция", "SRV_LAZER"));
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineButton("Аппаратный массаж", "SRV_MASSAGE"));
        
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createInlineButton("Почасовая ставка", "SRV_HOURLY"));
        
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(createInlineButton("Отмена", "CANCEL"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(row4);
        markup.setKeyboard(rows);
        return markup;
    }
    
    public static InlineKeyboardMarkup getCalendarKeyboard(LocalDate date, List<LocalDate> activeDates) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Header: Month Year
        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        String monthYear = date.getMonth().name() + " " + date.getYear();
        headerRow.add(createInlineButton(monthYear, "IGNORE"));
        rows.add(headerRow);

        // Days of week
        List<InlineKeyboardButton> daysOfWeekRow = new ArrayList<>();
        String[] daysOfWeek = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (String day : daysOfWeek) {
            daysOfWeekRow.add(createInlineButton(day, "IGNORE"));
        }
        rows.add(daysOfWeekRow);

        // Days grid
        int daysInMonth = date.lengthOfMonth();
        int firstDayOfWeek = date.withDayOfMonth(1).getDayOfWeek().getValue(); // 1 = Monday, 7 = Sunday
        
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        // Empty slots before the 1st
        for (int i = 1; i < firstDayOfWeek; i++) {
            currentRow.add(createInlineButton(" ", "IGNORE"));
        }

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate currentDay = date.withDayOfMonth(day);
            String label = String.valueOf(day);
            boolean isActive = activeDates != null && activeDates.contains(currentDay);
            
            if (activeDates != null) {
                // If we provided active dates, only mark and enable those
                if (isActive) {
                    label = "• " + label;
                    currentRow.add(createInlineButton(label, "CAL_DAY_" + currentDay.toString()));
                } else {
                    currentRow.add(createInlineButton(label, "IGNORE"));
                }
            } else {
                // Normal mode
                currentRow.add(createInlineButton(label, "CAL_DAY_" + currentDay.toString()));
            }

            if (currentRow.size() == 7) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }

        // Empty slots after the last day
        if (!currentRow.isEmpty()) {
            while (currentRow.size() < 7) {
                currentRow.add(createInlineButton(" ", "IGNORE"));
            }
            rows.add(currentRow);
        }

        // Navigation
        List<InlineKeyboardButton> navRow = new ArrayList<>();
        navRow.add(createInlineButton("<", "CAL_PREV_" + date.minusMonths(1).withDayOfMonth(1).toString()));
        navRow.add(createInlineButton("Отмена", "CANCEL"));
        navRow.add(createInlineButton(">", "CAL_NEXT_" + date.plusMonths(1).withDayOfMonth(1).toString()));
        rows.add(navRow);

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup getStatisticsDateSelectionKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineButton("Сегодня", "STAT_TODAY"));
        row1.add(createInlineButton("Вчера", "STAT_YESTERDAY"));
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineButton("📅 Выбрать дату", "STAT_CALENDAR"));
        row2.add(createInlineButton("Отмена", "CANCEL"));

        rows.add(row1);
        rows.add(row2);
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup getSettingsKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineButton("Управление услугами", "MANAGE_SERVICES"));
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineButton("Изменить ставку в час", "SET_HOURLY"));

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createInlineButton("Изменить налог в месяц", "SET_TAX"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup getManageServicesKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineButton("➕ Добавить услугу", "ADD_SERVICE"));
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineButton("✏️ Переименовать", "RENAME_SERVICE"));
        
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createInlineButton("🔄 Изменить процент", "UPDATE_SERVICE"));

        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(createInlineButton("❌ Удалить услугу", "DELETE_SERVICE"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(row4);

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup getDynamicServiceSelectionKeyboard(java.util.List<com.payroll.bot.entity.ServiceCategory> services) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (com.payroll.bot.entity.ServiceCategory svc : services) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createInlineButton(svc.getName() + " (" + svc.getPercentage() + "%)", "SRV_DYN_" + svc.getId()));
            rows.add(row);
        }

        List<InlineKeyboardButton> hourlyRow = new ArrayList<>();
        hourlyRow.add(createInlineButton("Почасовая работа", "SRV_HOURLY"));
        rows.add(hourlyRow);

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup getServiceListKeyboard(java.util.List<com.payroll.bot.entity.ServiceCategory> services, String prefix) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (com.payroll.bot.entity.ServiceCategory svc : services) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createInlineButton(svc.getName(), prefix + svc.getId()));
            rows.add(row);
        }

        List<InlineKeyboardButton> cancelRow = new ArrayList<>();
        cancelRow.add(createInlineButton("Отмена", "CANCEL"));
        rows.add(cancelRow);

        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup getOnboardingFinishKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton("✅ Завершить", "FINISH_ONBOARDING_SERVICES"));
        rows.add(row);
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup getUnclosedMonthsKeyboard(List<java.time.YearMonth> unclosedMonths) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        for (java.time.YearMonth ym : unclosedMonths) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            String monthName = ym.getMonth().getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, new java.util.Locale("ru"));
            String btnText = monthName.substring(0, 1).toUpperCase() + monthName.substring(1) + " " + ym.getYear();
            row.add(createInlineButton(btnText, "CLOSE_MONTH_SELECT_" + ym.getMonthValue() + "_" + ym.getYear()));
            rows.add(row);
        }
        
        List<InlineKeyboardButton> rowCancel = new ArrayList<>();
        rowCancel.add(createInlineButton("❌ Отмена", "CANCEL_CLOSE_MONTH"));
        rows.add(rowCancel);
        
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup getCloseMonthConfirmationKeyboard(int month, int year) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineButton("✅ Да", "CONFIRM_CLOSE_MONTH_" + month + "_" + year));
        row1.add(createInlineButton("❌ Нет", "CANCEL_CLOSE_MONTH"));
        
        rows.add(row1);
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup getBudgetEditKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineButton("Аренда", "EDIT_BUDGET_RENT_BTN"));
        row1.add(createInlineButton("Еда", "EDIT_BUDGET_FOOD_BTN"));
        row1.add(createInlineButton("Допы", "EDIT_BUDGET_ADDITIONAL_BTN"));
        
        rows.add(row1);
        markup.setKeyboard(rows);
        return markup;
    }

    private static InlineKeyboardButton createInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}
