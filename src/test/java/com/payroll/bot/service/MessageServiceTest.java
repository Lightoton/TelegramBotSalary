package com.payroll.bot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private MessageService messageService;

    @Test
    void testGetMessage() {
        when(messageSource.getMessage(eq("onboarding.welcome"), any(), eq(new Locale("ru"))))
                .thenReturn("Добро пожаловать!");

        String result = messageService.getMessage("onboarding.welcome");
        assertEquals("Добро пожаловать!", result);
    }

    @Test
    void testGetMessageWithArgs() {
        Object[] args = new Object[]{"Маникюр"};
        when(messageSource.getMessage(eq("onboarding.service.percentage"), eq(args), eq(new Locale("ru"))))
                .thenReturn("Введите процент для Маникюр:");

        String result = messageService.getMessage("onboarding.service.percentage", args);
        assertEquals("Введите процент для Маникюр:", result);
    }
}
