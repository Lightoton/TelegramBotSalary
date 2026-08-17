package com.payroll.bot.telegram;

import com.payroll.bot.service.BudgetService;
import com.payroll.bot.service.CalculationService;
import com.payroll.bot.service.SessionService;
import com.payroll.bot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class UpdateHandler {

    private final UserService userService;
    private final SessionService sessionService;
    private final CalculationService calculationService;
    private final BudgetService budgetService;

    @Value("${app.security.master-password}")
    private String masterPassword;

    public SendMessage handleUpdate(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            return handleMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            return handleCallback(update.getCallbackQuery());
        }
        return null;
    }

    private SendMessage handleMessage(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();
        UserSession session = sessionService.getSession(chatId);

        if (message.getText() != null && (message.getText().equals("/start") || message.getText().equals("/menu"))) {
            if (userService.isUserRegistered(chatId)) {
                session.setState(BotState.MAIN_MENU);
                SendMessage msg = createMessage(chatId, "🤖 Главное меню открыто!\n\n👆 <i>Пожалуйста, не удаляйте это сообщение, оно держит нижние кнопки.</i>\n\nЕсли кнопки вдруг пропадут, просто отправьте /menu");
                msg.setReplyMarkup(KeyboardUtils.getMainMenu());
                msg.setParseMode("HTML");
                return msg;
            } else {
                session.setState(BotState.AWAITING_PASSWORD);
            }
        }

        if (!userService.isUserRegistered(chatId)) {
            if (session.getState() != BotState.AWAITING_PASSWORD) {
                session.setState(BotState.AWAITING_PASSWORD);
            }
        } else {
            if (session.getState() == BotState.AWAITING_PASSWORD) {
                session.setState(BotState.MAIN_MENU);
            }
        }

        switch (session.getState()) {
            case AWAITING_PASSWORD:
                if (masterPassword.equals(text)) {
                    userService.registerUser(chatId);
                    session.setState(BotState.ONBOARDING_ADD_SERVICE_NAME);
                    return createMessage(chatId, "✅ Пароль верный. Добро пожаловать!\nДавайте добавим категории услуг.\n\nВведите название первой услуги (например, 'Лазер'):");
                } else {
                    return createMessage(chatId, "❌ Неверный пароль. Попробуйте еще раз:");
                }
            case ONBOARDING_ADD_SERVICE_NAME:
                session.setTempServiceName(text);
                session.setState(BotState.ONBOARDING_ADD_SERVICE_PERCENTAGE);
                return createMessage(chatId, "Какой процент от чека вы получаете за услугу '" + text + "'?");
            case ONBOARDING_ADD_SERVICE_PERCENTAGE:
                try {
                    BigDecimal percent = new BigDecimal(text.replace(",", "."));
                    userService.addServiceCategory(chatId, session.getTempServiceName(), percent);
                    session.setState(BotState.ONBOARDING_ADD_SERVICE_NAME);
                    SendMessage msg = createMessage(chatId, "✅ Услуга добавлена!\nЕсли хотите добавить еще одну услугу, просто отправьте в чат её название.\nЕсли это всё — нажмите 'Завершить'.");
                    msg.setReplyMarkup(KeyboardUtils.getOnboardingFinishKeyboard());
                    return msg;
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число (например 40).");
                }
            case ONBOARDING_HOURLY_RATE:
                try {
                    BigDecimal rate = new BigDecimal(text.replace(",", "."));
                    if (rate.compareTo(BigDecimal.ZERO) < 0) {
                        return createMessage(chatId, "Ставка не может быть отрицательной. Пожалуйста, введите корректное число:");
                    }
                    userService.updateHourlyRate(chatId, rate);
                    session.setState(BotState.ONBOARDING_MONTHLY_TAX);
                    return createMessage(chatId, "✅ Ставка сохранена!\n\nИ последнее: введите ваш фиксированный налог в месяц. Если налога нет, введите 0:");
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }
            case ONBOARDING_MONTHLY_TAX:
                try {
                    BigDecimal tax = new BigDecimal(text.replace(",", "."));
                    if (tax.compareTo(BigDecimal.ZERO) < 0) {
                        return createMessage(chatId, "Налог не может быть отрицательным. Пожалуйста, введите корректное число:");
                    }
                    userService.updateMonthlyTax(chatId, tax);
                    session.setState(BotState.MAIN_MENU);
                    SendMessage msg = createMessage(chatId, "✅ <b>Регистрация завершена!</b>\n\n🤖 Я готов к работе!\n\n👆 <i>Пожалуйста, не удаляйте это сообщение, оно держит нижние кнопки.</i>\n\nЕсли кнопки вдруг пропадут, просто отправьте /menu");
                    msg.setReplyMarkup(KeyboardUtils.getMainMenu());
                    msg.setParseMode("HTML");
                    return msg;
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }
            case SETTINGS_ADD_SERVICE_NAME:
                session.setTempServiceName(text);
                session.setState(BotState.SETTINGS_ADD_SERVICE_PERCENTAGE);
                return createMessage(chatId, "Введите процент для услуги '" + text + "':");
            case SETTINGS_ADD_SERVICE_PERCENTAGE:
                try {
                    BigDecimal percent = new BigDecimal(text.replace(",", "."));
                    userService.addServiceCategory(chatId, session.getTempServiceName(), percent);
                    session.setState(BotState.MAIN_MENU);
                    return createMessage(chatId, "✅ Услуга '" + session.getTempServiceName() + "' успешно добавлена!");
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }
            case SETTINGS_RENAME_SERVICE_NAME:
                userService.renameService(session.getTempServiceId(), text);
                session.setState(BotState.MAIN_MENU);
                return createMessage(chatId, "✅ Услуга переименована в '" + text + "'!");
            case SETTINGS_UPDATE_SERVICE_PERCENTAGE:
                try {
                    BigDecimal percent = new BigDecimal(text.replace(",", "."));
                    userService.updateServicePercentage(session.getTempServiceId(), percent);
                    session.setState(BotState.MAIN_MENU);
                    return createMessage(chatId, "✅ Процент услуги обновлен!");
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }
            case ADD_INCOME_VALUE:
                try {
                    com.payroll.bot.entity.TransactionRecord record = calculationService.addIncome(chatId, session.getTempDate(), session.getTempServiceType(), text);
                    session.setState(BotState.MAIN_MENU);
                    String responseMsg = "✅ Доход успешно добавлен!\n\n" +
                                     "🔹 Услуга: " + record.getServiceType() + "\n";
                    if ("HOURLY".equals(record.getServiceType())) {
                        responseMsg += "🔹 Отработано часов: " + record.getInputValue() + "\n";
                    } else {
                        responseMsg += "🔹 Стоимость услуги: " + record.getInputValue() + " €\n";
                    }
                    responseMsg += "💰 Ваш заработок: <b>" + record.getEarnedAmount() + " €</b>";
                    
                    return createMessage(chatId, responseMsg);
                } catch (Exception e) {
                    return createMessage(chatId, "Ошибка: " + e.getMessage() + "\nПопробуйте еще раз или нажмите /start для отмены.");
                }
            case EDIT_HOURLY_RATE:
                try {
                    BigDecimal rate = new BigDecimal(text.replace(",", "."));
                    if (rate.compareTo(BigDecimal.ZERO) < 0) {
                        return createMessage(chatId, "Ставка не может быть отрицательной. Пожалуйста, введите корректное число:");
                    }
                    userService.updateHourlyRate(chatId, rate);
                    session.setState(BotState.MAIN_MENU);
                    return createMessage(chatId, "✅ Почасовая ставка обновлена!");
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }
            case EDIT_TRANSACTION_VALUE:
                try {
                    calculationService.updateTransaction(session.getTempTransactionId(), text);
                    session.setState(BotState.MAIN_MENU);
                    return createMessage(chatId, "✅ Запись успешно обновлена!");
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите корректное число.");
                }
            case EDIT_TAX:
                try {
                    BigDecimal tax = new BigDecimal(text.replace(",", "."));
                    if (tax.compareTo(BigDecimal.ZERO) < 0) {
                        return createMessage(chatId, "Налог не может быть отрицательным. Пожалуйста, введите корректное число:");
                    }
                    userService.updateMonthlyTax(chatId, tax);
                    session.setState(BotState.MAIN_MENU);
                    return createMessage(chatId, "✅ Сумма налога обновлена!");
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }

            case BUDGET_SETUP_RENT:
                try {
                    BigDecimal rent = new BigDecimal(text.replace(",", "."));
                    if (rent.compareTo(BigDecimal.ZERO) < 0) {
                        return createMessage(chatId, "Значение не может быть отрицательным. Пожалуйста, введите корректное число:");
                    }
                    userService.updateRentLimit(chatId, rent);
                    session.setState(BotState.BUDGET_SETUP_FOOD);
                    return createMessage(chatId, "Сколько вы тратите на продукты? На еду, понятно, еда, можете с запасом указать с кафешками.");
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }

            case BUDGET_SETUP_FOOD:
                try {
                    BigDecimal food = new BigDecimal(text.replace(",", "."));
                    if (food.compareTo(BigDecimal.ZERO) < 0) {
                        return createMessage(chatId, "Значение не может быть отрицательным. Пожалуйста, введите корректное число:");
                    }
                    userService.updateFoodLimit(chatId, food);
                    session.setState(BotState.BUDGET_SETUP_ADDITIONAL);
                    return createMessage(chatId, "Если у вас есть какие-то дополнительные траты, пожалуйста, тоже их укажите.");
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }

            case BUDGET_SETUP_ADDITIONAL:
                try {
                    BigDecimal additional = new BigDecimal(text.replace(",", "."));
                    if (additional.compareTo(BigDecimal.ZERO) < 0) {
                        return createMessage(chatId, "Значение не может быть отрицательным. Пожалуйста, введите корректное число:");
                    }
                    userService.updateAdditionalLimit(chatId, additional);
                    session.setState(BotState.MAIN_MENU);
                    
                    LocalDate now = LocalDate.now();
                    String report = budgetService.generateBudgetReport(chatId, now.getMonthValue(), now.getYear());
                    SendMessage msg = createMessage(chatId, report);
                    msg.setReplyMarkup(KeyboardUtils.getBudgetEditKeyboard());
                    return msg;
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }

            case EDIT_BUDGET_RENT:
                try {
                    BigDecimal rent = new BigDecimal(text.replace(",", "."));
                    if (rent.compareTo(BigDecimal.ZERO) < 0) {
                        return createMessage(chatId, "Значение не может быть отрицательным. Пожалуйста, введите корректное число:");
                    }
                    userService.updateRentLimit(chatId, rent);
                    session.setState(BotState.MAIN_MENU);
                    LocalDate now = LocalDate.now();
                    SendMessage msg = createMessage(chatId, budgetService.generateBudgetReport(chatId, now.getMonthValue(), now.getYear()));
                    msg.setReplyMarkup(KeyboardUtils.getBudgetEditKeyboard());
                    return msg;
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }

            case EDIT_BUDGET_FOOD:
                try {
                    BigDecimal food = new BigDecimal(text.replace(",", "."));
                    if (food.compareTo(BigDecimal.ZERO) < 0) {
                        return createMessage(chatId, "Значение не может быть отрицательным. Пожалуйста, введите корректное число:");
                    }
                    userService.updateFoodLimit(chatId, food);
                    session.setState(BotState.MAIN_MENU);
                    LocalDate now = LocalDate.now();
                    SendMessage msg = createMessage(chatId, budgetService.generateBudgetReport(chatId, now.getMonthValue(), now.getYear()));
                    msg.setReplyMarkup(KeyboardUtils.getBudgetEditKeyboard());
                    return msg;
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }

            case EDIT_BUDGET_ADDITIONAL:
                try {
                    BigDecimal additional = new BigDecimal(text.replace(",", "."));
                    if (additional.compareTo(BigDecimal.ZERO) < 0) {
                        return createMessage(chatId, "Значение не может быть отрицательным. Пожалуйста, введите корректное число:");
                    }
                    userService.updateAdditionalLimit(chatId, additional);
                    session.setState(BotState.MAIN_MENU);
                    LocalDate now = LocalDate.now();
                    SendMessage msg = createMessage(chatId, budgetService.generateBudgetReport(chatId, now.getMonthValue(), now.getYear()));
                    msg.setReplyMarkup(KeyboardUtils.getBudgetEditKeyboard());
                    return msg;
                } catch (Exception e) {
                    return createMessage(chatId, "Пожалуйста, введите число.");
                }
            case MAIN_MENU:
                return handleMainMenuCommands(chatId, text, session);
            default:
                return createMessage(chatId, "Неизвестная команда. Нажмите /start");
        }
    }

    private SendMessage handleMainMenuCommands(Long chatId, String text, UserSession session) {
        if ("💰 Добавить доход".equals(text)) {
            session.setState(BotState.ADD_INCOME_DATE);
            SendMessage msg = createMessage(chatId, "Выберите дату дохода:");
            msg.setReplyMarkup(KeyboardUtils.getDateSelectionKeyboard());
            return msg;
        } else if ("📊 Статистика".equals(text)) {
            LocalDate now = LocalDate.now();
            BigDecimal earned = calculationService.getMonthlyTotal(chatId, now.getMonthValue(), now.getYear());
            com.payroll.bot.entity.UserSettings settings = userService.getUserSettings(chatId);
            
            String msgText = "💰 Текущий месяц (" + now.getMonthValue() + "/" + now.getYear() + ")\nВсего заработано: " + earned + " €";
            if (settings.getMonthlyTax() != null && settings.getMonthlyTax().compareTo(BigDecimal.ZERO) > 0) {
                msgText += "\n\n❗️ Напоминание: ваш фиксированный налог за месяц составляет " + settings.getMonthlyTax() + " €";
            }
            
            SendMessage msg = createMessage(chatId, msgText);
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();
            java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new java.util.ArrayList<>();
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btn = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
            btn.setText("Подробная статистика");
            btn.setCallbackData("STAT_MENU");
            row.add(btn);
            rows.add(row);
            markup.setKeyboard(rows);
            msg.setReplyMarkup(markup);
            return msg;
        } else if ("🔒 Закрыть месяц".equals(text)) {
            java.util.List<java.time.YearMonth> unclosed = calculationService.getUnclosedMonths(chatId);
            if (unclosed.isEmpty()) {
                return createMessage(chatId, "У вас нет открытых месяцев для закрытия.");
            }
            SendMessage msg = createMessage(chatId, "Выберите месяц, который хотите закрыть:");
            msg.setReplyMarkup(KeyboardUtils.getUnclosedMonthsKeyboard(unclosed));
            return msg;
        } else if ("📁 История".equals(text)) {
            java.util.List<com.payroll.bot.entity.MonthlyReport> reports = calculationService.getArchivedMonths(chatId);
            if (reports.isEmpty()) {
                return createMessage(chatId, "В истории пока еще не найдены месяца.");
            }
            SendMessage msg = createMessage(chatId, "Выберите месяц из архива:");
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
            java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();
            for (com.payroll.bot.entity.MonthlyReport report : reports) {
                java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new java.util.ArrayList<>();
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btn = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
                btn.setText(report.getReportMonth() + "/" + report.getReportYear() + " — " + report.getTotalEarned() + " €");
                btn.setCallbackData("HISTORY_" + report.getReportMonth() + "_" + report.getReportYear());
                row.add(btn);
                rows.add(row);
            }
            markup.setKeyboard(rows);
            msg.setReplyMarkup(markup);
            return msg;
        } else if ("🏠 На жизнь".equals(text)) {
            com.payroll.bot.entity.UserSettings settings = userService.getUserSettings(chatId);
            if (settings.getRentLimit().compareTo(BigDecimal.ZERO) < 0) {
                session.setState(BotState.BUDGET_SETUP_RENT);
                return createMessage(chatId, "Какая у вас аренда? В аренде, пожалуйста, укажите, помимо аренды, также коммунальные, интернет и вот это вот всё.");
            } else {
                LocalDate now = LocalDate.now();
                String report = budgetService.generateBudgetReport(chatId, now.getMonthValue(), now.getYear());
                SendMessage msg = createMessage(chatId, report);
                msg.setReplyMarkup(KeyboardUtils.getBudgetEditKeyboard());
                return msg;
            }
        } else if ("⚙️ Настройки".equals(text)) {
            SendMessage msg = createMessage(chatId, "Выберите, что хотите изменить:");
            msg.setReplyMarkup(KeyboardUtils.getSettingsKeyboard());
            return msg;
        } else {
            return createMessage(chatId, "Выберите действие из меню.");
        }
    }

    private SendMessage showDetailedStatsForDate(Long chatId, LocalDate date) {
        java.util.List<com.payroll.bot.entity.TransactionRecord> txs = calculationService.getTransactionsByMonth(chatId, date.getMonthValue(), date.getYear())
                .stream().filter(tx -> tx.getTransactionDate().equals(date)).toList();

        if (txs.isEmpty()) {
            return createMessage(chatId, "Нет записей за " + date);
        }

        java.math.BigDecimal dailyTotal = txs.stream()
                .map(com.payroll.bot.entity.TransactionRecord::getEarnedAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        SendMessage msg = createMessage(chatId, "Транзакции за " + date + ":\nЗаработано за день: " + dailyTotal + " €\nВыберите запись для редактирования:");
        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();

        int index = 1;
        for (com.payroll.bot.entity.TransactionRecord tx : txs) {
            java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new java.util.ArrayList<>();
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btn = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
            btn.setText(index + ". " + tx.getServiceType() + " | В: " + tx.getInputValue() + " | € %: " + tx.getEarnedAmount() + " 💶");
            if (calculationService.isMonthClosed(chatId, date.getMonthValue(), date.getYear())) {
                btn.setCallbackData("IGNORE");
                btn.setText("🔒 " + btn.getText());
            } else {
                btn.setCallbackData("EDIT_TX_" + tx.getId());
            }
            row.add(btn);
            rows.add(row);
            index++;
        }
        
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> cancelRow = new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton cancelBtn = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        cancelBtn.setText("Отмена");
        cancelBtn.setCallbackData("CANCEL");
        cancelRow.add(cancelBtn);
        rows.add(cancelRow);

        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }

    private SendMessage handleCallback(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();
        UserSession session = sessionService.getSession(chatId);

        if ("CANCEL".equals(data)) {
            session.setState(BotState.MAIN_MENU);
            return createMessage(chatId, "Отменено. Выберите действие:");
        }

        if ("IGNORE".equals(data)) {
            return null;
        }

        if ("FINISH_ONBOARDING_SERVICES".equals(data)) {
            session.setState(BotState.ONBOARDING_HOURLY_RATE);
            return createMessage(chatId, "А теперь, пожалуйста, введите свою почасовую ставку в евро (например, 12.50):");
        }

        if ("MANAGE_SERVICES".equals(data)) {
            SendMessage msg = createMessage(chatId, "Управление услугами:");
            msg.setReplyMarkup(KeyboardUtils.getManageServicesKeyboard());
            return msg;
        }

        if ("ADD_SERVICE".equals(data)) {
            session.setState(BotState.SETTINGS_ADD_SERVICE_NAME);
            return createMessage(chatId, "Введите название новой услуги:");
        }

        if ("RENAME_SERVICE".equals(data)) {
            java.util.List<com.payroll.bot.entity.ServiceCategory> services = userService.getServiceCategories(chatId);
            if (services.isEmpty()) return createMessage(chatId, "У вас пока нет добавленных услуг.");
            session.setState(BotState.SETTINGS_RENAME_SERVICE_NAME);
            SendMessage msg = createMessage(chatId, "Выберите услугу для переименования:");
            msg.setReplyMarkup(KeyboardUtils.getServiceListKeyboard(services, "REN_SRV_"));
            return msg;
        }

        if ("UPDATE_SERVICE".equals(data)) {
            java.util.List<com.payroll.bot.entity.ServiceCategory> services = userService.getServiceCategories(chatId);
            if (services.isEmpty()) return createMessage(chatId, "У вас пока нет добавленных услуг.");
            SendMessage msg = createMessage(chatId, "Выберите услугу для изменения процента:");
            msg.setReplyMarkup(KeyboardUtils.getServiceListKeyboard(services, "UPD_SRV_"));
            return msg;
        }

        if ("DELETE_SERVICE".equals(data)) {
            java.util.List<com.payroll.bot.entity.ServiceCategory> services = userService.getServiceCategories(chatId);
            if (services.isEmpty()) return createMessage(chatId, "У вас пока нет добавленных услуг.");
            SendMessage msg = createMessage(chatId, "Выберите услугу для удаления:");
            msg.setReplyMarkup(KeyboardUtils.getServiceListKeyboard(services, "DEL_SRV_"));
            return msg;
        }

        if (data.startsWith("REN_SRV_")) {
            Long srvId = Long.parseLong(data.substring("REN_SRV_".length()));
            session.setTempServiceId(srvId);
            session.setState(BotState.SETTINGS_RENAME_SERVICE_NAME);
            return createMessage(chatId, "Введите новое название для этой услуги:");
        }

        if (data.startsWith("UPD_SRV_")) {
            Long srvId = Long.parseLong(data.substring("UPD_SRV_".length()));
            session.setTempServiceId(srvId);
            session.setState(BotState.SETTINGS_UPDATE_SERVICE_PERCENTAGE);
            return createMessage(chatId, "Введите новый процент для этой услуги (например 50):");
        }

        if (data.startsWith("DEL_SRV_")) {
            Long srvId = Long.parseLong(data.substring("DEL_SRV_".length()));
            userService.deleteService(srvId);
            return createMessage(chatId, "✅ Услуга успешно удалена!");
        }

        if (data.startsWith("SET_")) {
            if ("SET_HOURLY".equals(data)) {
                session.setState(BotState.EDIT_HOURLY_RATE);
                return createMessage(chatId, "Введите новую почасовую ставку:");
            } else if ("SET_TAX".equals(data)) {
                session.setState(BotState.EDIT_TAX);
                return createMessage(chatId, "Введите сумму фиксированного налога в месяц (например 150):");
            }
        }

        if (data.startsWith("CLOSE_MONTH_SELECT_")) {
            String[] parts = data.split("_");
            int month = Integer.parseInt(parts[3]);
            int year = Integer.parseInt(parts[4]);
            String monthName = java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, new java.util.Locale("ru"));
            String btnText = monthName.substring(0, 1).toUpperCase() + monthName.substring(1) + " " + year;
            
            SendMessage msg = createMessage(chatId, "Вы уверены, что хотите закрыть <b>" + btnText + "</b>?");
            msg.setReplyMarkup(KeyboardUtils.getCloseMonthConfirmationKeyboard(month, year));
            msg.setParseMode("HTML");
            return msg;
        }

        if (data.startsWith("CONFIRM_CLOSE_MONTH_")) {
            try {
                String[] parts = data.split("_");
                int month = Integer.parseInt(parts[3]);
                int year = Integer.parseInt(parts[4]);
                calculationService.closeMonth(chatId, month, year);
                return createMessage(chatId, "✅ Месяц успешно закрыт и добавлен в архив!");
            } catch (Exception e) {
                return createMessage(chatId, "❌ " + e.getMessage());
            }
        }

        if ("CANCEL_CLOSE_MONTH".equals(data)) {
            session.setState(BotState.MAIN_MENU);
            return createMessage(chatId, "Действие отменено.");
        }

        if ("EDIT_BUDGET_RENT_BTN".equals(data)) {
            session.setState(BotState.EDIT_BUDGET_RENT);
            return createMessage(chatId, "Какая у вас аренда? В аренде, пожалуйста, укажите, помимо аренды, также коммунальные, интернет и вот это вот всё:");
        }
        
        if ("EDIT_BUDGET_FOOD_BTN".equals(data)) {
            session.setState(BotState.EDIT_BUDGET_FOOD);
            return createMessage(chatId, "Сколько вы тратите на продукты? На еду, понятно, еда, можете с запасом указать с кафешками:");
        }
        
        if ("EDIT_BUDGET_ADDITIONAL_BTN".equals(data)) {
            session.setState(BotState.EDIT_BUDGET_ADDITIONAL);
            return createMessage(chatId, "Если у вас есть какие-то дополнительные траты, пожалуйста, тоже их укажите:");
        }

        if (data.startsWith("HISTORY_")) {
            if (data.startsWith("HISTORY_DETAIL_")) {
                String[] parts = data.split("_");
                int m = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);
                java.util.List<com.payroll.bot.entity.TransactionRecord> txs = calculationService.getTransactionsByMonth(chatId, m, y);
                if (txs.isEmpty()) {
                    return createMessage(chatId, "Транзакции не найдены.");
                }
                StringBuilder sb = new StringBuilder("Детализация за " + m + "/" + y + ":\n");
                for (com.payroll.bot.entity.TransactionRecord tx : txs) {
                    sb.append(tx.getTransactionDate()).append(" | ").append(tx.getServiceType()).append(" | ").append(tx.getEarnedAmount()).append(" €\n");
                }
                return createMessage(chatId, sb.toString());
            } else {
                String[] parts = data.split("_");
                int m = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                com.payroll.bot.entity.MonthlyReport rep = calculationService.getArchivedMonths(chatId).stream()
                    .filter(r -> r.getReportMonth() == m && r.getReportYear() == y).findFirst().orElse(null);
                if (rep != null) {
                    SendMessage msg = createMessage(chatId, "Архив " + m + "/" + y + "\nВсего заработано: " + rep.getTotalEarned() + " €");
                    org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                    java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();
                    java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new java.util.ArrayList<>();
                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btn = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
                    btn.setText("Детальная зарплата за месяц");
                    btn.setCallbackData("HISTORY_DETAIL_" + m + "_" + y);
                    row.add(btn);
                    rows.add(row);
                    markup.setKeyboard(rows);
                    msg.setReplyMarkup(markup);
                    return msg;
                }
            }
        }

        if (data.equals("STAT_MENU")) {
            session.setState(BotState.MAIN_MENU);
            SendMessage msg = createMessage(chatId, "За какой день показать статистику?");
            msg.setReplyMarkup(KeyboardUtils.getStatisticsDateSelectionKeyboard());
            return msg;
        }
        
        if (data.startsWith("STAT_")) {
            if ("STAT_TODAY".equals(data)) {
                return showDetailedStatsForDate(chatId, LocalDate.now());
            } else if ("STAT_YESTERDAY".equals(data)) {
                return showDetailedStatsForDate(chatId, LocalDate.now().minusDays(1));
            } else if ("STAT_CALENDAR".equals(data)) {
                LocalDate now = LocalDate.now();
                java.util.List<LocalDate> activeDates = calculationService.getTransactionsByMonth(chatId, now.getMonthValue(), now.getYear())
                        .stream().map(com.payroll.bot.entity.TransactionRecord::getTransactionDate).distinct().toList();
                SendMessage msg = createMessage(chatId, "Выберите день:");
                msg.setReplyMarkup(KeyboardUtils.getCalendarKeyboard(now, activeDates));
                session.setState(BotState.MAIN_MENU);
                return msg;
            }
        }
        
        if (data.startsWith("EDIT_TX_")) {
            Long txId = Long.parseLong(data.substring("EDIT_TX_".length()));
            com.payroll.bot.entity.TransactionRecord record = calculationService.getTransactionById(txId);
            if (calculationService.isMonthClosed(chatId, record.getTransactionDate().getMonthValue(), record.getTransactionDate().getYear())) {
                return createMessage(chatId, "❌ Этот месяц закрыт, редактирование невозможно.");
            }
            session.setTempTransactionId(txId);
            session.setState(BotState.EDIT_TRANSACTION_VALUE);
            return createMessage(chatId, "Введите новую сумму для этой услуги (например 120):");
        }

        if (session.getState() == BotState.ADD_INCOME_DATE || data.startsWith("CAL_DAY_") || data.startsWith("CAL_PREV_") || data.startsWith("CAL_NEXT_")) {
            if ("DATE_TODAY".equals(data)) {
                session.setTempDate(LocalDate.now());
            } else if ("DATE_YESTERDAY".equals(data)) {
                session.setTempDate(LocalDate.now().minusDays(1));
            } else if ("DATE_CALENDAR".equals(data)) {
                SendMessage msg = createMessage(chatId, "Выберите день:");
                msg.setReplyMarkup(KeyboardUtils.getCalendarKeyboard(LocalDate.now(), null));
                return msg;
            } else if (data.startsWith("CAL_DAY_")) {
                LocalDate date = LocalDate.parse(data.substring("CAL_DAY_".length()));
                if (session.getState() == BotState.MAIN_MENU) {
                    return showDetailedStatsForDate(chatId, date);
                }
                session.setTempDate(date);
            } else if (data.startsWith("CAL_PREV_") || data.startsWith("CAL_NEXT_")) {
                LocalDate date = LocalDate.parse(data.substring(9));
                SendMessage msg = createMessage(chatId, "Выберите день:");
                if (session.getState() == BotState.MAIN_MENU) {
                     java.util.List<LocalDate> activeDates = calculationService.getTransactionsByMonth(chatId, date.getMonthValue(), date.getYear())
                        .stream().map(com.payroll.bot.entity.TransactionRecord::getTransactionDate).distinct().toList();
                     msg.setReplyMarkup(KeyboardUtils.getCalendarKeyboard(date, activeDates));
                } else {
                     msg.setReplyMarkup(KeyboardUtils.getCalendarKeyboard(date, null));
                }
                return msg;
            }

            if ("DATE_TODAY".equals(data) || "DATE_YESTERDAY".equals(data) || (data.startsWith("CAL_DAY_") && session.getState() == BotState.ADD_INCOME_DATE)) {
                session.setState(BotState.ADD_INCOME_SERVICE);
                SendMessage msg = createMessage(chatId, "Выбрана дата: " + session.getTempDate() + "\nВыберите услугу:");
                msg.setReplyMarkup(KeyboardUtils.getDynamicServiceSelectionKeyboard(userService.getServiceCategories(chatId)));
                return msg;
            }
            return null;
        } else if (session.getState() == BotState.ADD_INCOME_SERVICE) {
            if (data.startsWith("SRV_DYN_")) {
                Long srvId = Long.parseLong(data.substring("SRV_DYN_".length()));
                com.payroll.bot.entity.ServiceCategory svc = userService.getServiceCategories(chatId).stream().filter(s -> s.getId().equals(srvId)).findFirst().orElse(null);
                if (svc != null) {
                    session.setTempServiceType(svc.getName());
                    session.setState(BotState.ADD_INCOME_VALUE);
                    return createMessage(chatId, "Введите стоимость услуги '" + svc.getName() + "' в евро (полную сумму):");
                }
            } else if ("SRV_HOURLY".equals(data)) {
                session.setTempServiceType("HOURLY");
                session.setState(BotState.ADD_INCOME_VALUE);
                return createMessage(chatId, "Введите временной интервал (формат HH:mm-HH:mm, например 13:00-15:30):");
            }
        }

        if ("IGNORE".equals(data)) {
            return createMessage(chatId, "⚠️ Это действие недоступно.");
        }

        return createMessage(chatId, "Действие не распознано.");
    }

    private SendMessage createMessage(Long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);
        msg.setParseMode("HTML");
        return msg;
    }
}
