package com.payroll.bot;

import com.payroll.bot.entity.BotUser;
import com.payroll.bot.entity.ServiceCategory;
import com.payroll.bot.entity.TransactionRecord;
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
import java.util.List;
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
    void isUserRegistered_ReturnsTrueAndFalse() {
        when(userRepository.existsById(chatId)).thenReturn(true);
        when(userRepository.existsById(999L)).thenReturn(false);

        assertTrue(userService.isUserRegistered(chatId));
        assertFalse(userService.isUserRegistered(999L));
    }

    @Test
    void registerUser_WhenUserNotRegistered_ShouldSaveUserAndSettings() {
        when(userRepository.existsById(chatId)).thenReturn(false);
        when(userSettingsRepository.existsById(chatId)).thenReturn(false);

        userService.registerUser(chatId);

        verify(userRepository, times(1)).save(any(BotUser.class));
        verify(userSettingsRepository, times(1)).save(any(UserSettings.class));
    }

    @Test
    void registerUser_WhenAlreadyRegistered_ShouldDoNothing() {
        when(userRepository.existsById(chatId)).thenReturn(true);

        userService.registerUser(chatId);

        verify(userRepository, never()).save(any());
        verify(userSettingsRepository, never()).save(any());
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
    void getServiceCategories_ShouldReturnList() {
        List<ServiceCategory> list = List.of(new ServiceCategory());
        when(serviceCategoryRepository.findByChatId(chatId)).thenReturn(list);

        assertEquals(list, userService.getServiceCategories(chatId));
    }

    @Test
    void updateServicePercentage_FoundAndNotFound() {
        ServiceCategory category = new ServiceCategory();
        category.setId(1L);
        when(serviceCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(serviceCategoryRepository.findById(2L)).thenReturn(Optional.empty());

        userService.updateServicePercentage(1L, new BigDecimal("65"));
        assertEquals(new BigDecimal("65"), category.getPercentage());
        verify(serviceCategoryRepository).save(category);

        assertThrows(IllegalArgumentException.class, () -> userService.updateServicePercentage(2L, new BigDecimal("50")));
    }

    @Test
    void renameService_FoundAndNotFound_UpdatesTransactions() {
        ServiceCategory category = new ServiceCategory();
        category.setId(1L);
        category.setChatId(chatId);
        category.setName("Старое имя");

        TransactionRecord tx1 = new TransactionRecord();
        tx1.setServiceType("Старое имя");
        TransactionRecord tx2 = new TransactionRecord();
        tx2.setServiceType("Другое");

        when(serviceCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(serviceCategoryRepository.findById(2L)).thenReturn(Optional.empty());
        when(transactionRecordRepository.findByChatIdOrderByTransactionDateDesc(chatId)).thenReturn(List.of(tx1, tx2));

        userService.renameService(1L, "Новое имя");
        assertEquals("Новое имя", category.getName());
        assertEquals("Новое имя", tx1.getServiceType());
        assertEquals("Другое", tx2.getServiceType());
        verify(transactionRecordRepository, times(1)).save(tx1);
        verify(transactionRecordRepository, never()).save(tx2);

        assertThrows(IllegalArgumentException.class, () -> userService.renameService(2L, "Любое"));
    }

    @Test
    void deleteService_CallsRepository() {
        userService.deleteService(5L);
        verify(serviceCategoryRepository).deleteById(5L);
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

    @Test
    void updateMonthlyTax_ShouldUpdateSettings() {
        UserSettings settings = new UserSettings();
        settings.setChatId(chatId);

        when(userSettingsRepository.findById(chatId)).thenReturn(Optional.of(settings));

        userService.updateMonthlyTax(chatId, new BigDecimal("200"));
        assertEquals(new BigDecimal("200"), settings.getMonthlyTax());
        verify(userSettingsRepository).save(settings);
    }

    @Test
    void updateLimits_RentFoodAdditional() {
        UserSettings settings = new UserSettings();
        settings.setChatId(chatId);
        when(userSettingsRepository.findById(chatId)).thenReturn(Optional.of(settings));

        userService.updateRentLimit(chatId, new BigDecimal("600"));
        assertEquals(new BigDecimal("600"), settings.getRentLimit());

        userService.updateFoodLimit(chatId, new BigDecimal("400"));
        assertEquals(new BigDecimal("400"), settings.getFoodLimit());

        userService.updateAdditionalLimit(chatId, new BigDecimal("100"));
        assertEquals(new BigDecimal("100"), settings.getAdditionalLimit());

        verify(userSettingsRepository, times(3)).save(settings);
    }
}
