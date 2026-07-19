package com.payroll.bot;

import com.payroll.bot.entity.BotUser;
import com.payroll.bot.entity.ServiceCategory;
import com.payroll.bot.entity.UserSettings;
import com.payroll.bot.repository.BotUserRepository;
import com.payroll.bot.repository.ServiceCategoryRepository;
import com.payroll.bot.repository.TransactionRecordRepository;
import com.payroll.bot.repository.UserSettingsRepository;
import com.payroll.bot.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private BotUserRepository userRepository;
    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private ServiceCategoryRepository serviceCategoryRepository;
    @Mock
    private TransactionRecordRepository transactionRecordRepository;

    @InjectMocks
    private UserService userService;

    private final Long chatId = 12345L;

    @Test
    void registerUser_WhenUserNotRegistered_ShouldSaveUserAndSettings() {
        when(userRepository.existsById(chatId)).thenReturn(false);
        when(userSettingsRepository.existsById(chatId)).thenReturn(false);

        userService.registerUser(chatId);

        verify(userRepository, times(1)).save(any(BotUser.class));
        verify(userSettingsRepository, times(1)).save(any(UserSettings.class));
    }

    @Test
    void getUserSettings_ShouldThrowExceptionIfNotFound() {
        when(userSettingsRepository.findById(chatId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.getUserSettings(chatId));
    }

    @Test
    void addServiceCategory_ShouldSaveCategory() {
        userService.addServiceCategory(chatId, "Massage", new BigDecimal("50"));

        verify(serviceCategoryRepository, times(1)).save(any(ServiceCategory.class));
    }

    @Test
    void updateHourlyRate_ShouldUpdateSettings() {
        UserSettings settings = new UserSettings();
        settings.setChatId(chatId);
        settings.setHourlyRate(BigDecimal.ZERO);
        
        when(userSettingsRepository.findById(chatId)).thenReturn(Optional.of(settings));

        userService.updateHourlyRate(chatId, new BigDecimal("15"));

        assertEquals(new BigDecimal("15"), settings.getHourlyRate());
        verify(userSettingsRepository, times(1)).save(settings);
    }
}
