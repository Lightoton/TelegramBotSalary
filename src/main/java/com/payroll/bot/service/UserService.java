package com.payroll.bot.service;

import com.payroll.bot.entity.BotUser;
import com.payroll.bot.entity.ServiceCategory;
import com.payroll.bot.entity.UserSettings;
import com.payroll.bot.repository.BotUserRepository;
import com.payroll.bot.repository.ServiceCategoryRepository;
import com.payroll.bot.repository.TransactionRecordRepository;
import com.payroll.bot.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final BotUserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final TransactionRecordRepository transactionRecordRepository;

    public boolean isUserRegistered(Long chatId) {
        return userRepository.existsById(chatId);
    }

    @Transactional
    public void registerUser(Long chatId) {
        if (!isUserRegistered(chatId)) {
            BotUser user = new BotUser(chatId, LocalDateTime.now());
            userRepository.save(user);
            
            if (!userSettingsRepository.existsById(chatId)) {
                UserSettings settings = new UserSettings();
                settings.setChatId(chatId);
                settings.setHourlyRate(BigDecimal.ZERO);
                settings.setMonthlyTax(BigDecimal.ZERO);
                userSettingsRepository.save(settings);
            }
        }
    }

    public UserSettings getUserSettings(Long chatId) {
        return userSettingsRepository.findById(chatId).orElseThrow(() -> new RuntimeException("Settings not found"));
    }

    @Transactional
    public void addServiceCategory(Long chatId, String name, BigDecimal percentage) {
        ServiceCategory category = new ServiceCategory();
        category.setChatId(chatId);
        category.setName(name);
        category.setPercentage(percentage);
        serviceCategoryRepository.save(category);
    }

    public List<ServiceCategory> getServiceCategories(Long chatId) {
        return serviceCategoryRepository.findByChatId(chatId);
    }

    @Transactional
    public void updateServicePercentage(Long serviceId, BigDecimal newPercentage) {
        ServiceCategory category = serviceCategoryRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));
        category.setPercentage(newPercentage);
        serviceCategoryRepository.save(category);
    }

    @Transactional
    public void renameService(Long serviceId, String newName) {
        ServiceCategory category = serviceCategoryRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));
        String oldName = category.getName();
        category.setName(newName);
        serviceCategoryRepository.save(category);
        
        List<com.payroll.bot.entity.TransactionRecord> txs = transactionRecordRepository.findByChatIdOrderByTransactionDateDesc(category.getChatId());
        for (com.payroll.bot.entity.TransactionRecord tx : txs) {
            if (tx.getServiceType().equals(oldName)) {
                tx.setServiceType(newName);
                transactionRecordRepository.save(tx);
            }
        }
    }

    @Transactional
    public void deleteService(Long serviceId) {
        serviceCategoryRepository.deleteById(serviceId);
    }

    @Transactional
    public void updateHourlyRate(Long chatId, BigDecimal rate) {
        UserSettings settings = getUserSettings(chatId);
        settings.setHourlyRate(rate);
        userSettingsRepository.save(settings);
    }

    @Transactional
    public void updateMonthlyTax(Long chatId, BigDecimal tax) {
        UserSettings settings = getUserSettings(chatId);
        settings.setMonthlyTax(tax);
        userSettingsRepository.save(settings);
    }

    @Transactional
    public void updateRentLimit(Long chatId, BigDecimal limit) {
        UserSettings settings = getUserSettings(chatId);
        settings.setRentLimit(limit);
        userSettingsRepository.save(settings);
    }

    @Transactional
    public void updateFoodLimit(Long chatId, BigDecimal limit) {
        UserSettings settings = getUserSettings(chatId);
        settings.setFoodLimit(limit);
        userSettingsRepository.save(settings);
    }

    @Transactional
    public void updateAdditionalLimit(Long chatId, BigDecimal limit) {
        UserSettings settings = getUserSettings(chatId);
        settings.setAdditionalLimit(limit);
        userSettingsRepository.save(settings);
    }
}
