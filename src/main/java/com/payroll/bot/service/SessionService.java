package com.payroll.bot.service;

import com.payroll.bot.entity.BotSession;
import com.payroll.bot.repository.BotSessionRepository;
import com.payroll.bot.telegram.BotState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final BotSessionRepository sessionRepository;
    private final com.payroll.bot.repository.BotUserRepository userRepository;

    @Transactional
    public BotSession getSession(Long chatId) {
        return sessionRepository.findById(chatId)
                .orElseGet(() -> {
                    BotSession session = new BotSession(chatId);
                    if (userRepository.existsById(chatId)) {
                        session.setState(BotState.MAIN_MENU);
                    }
                    return sessionRepository.save(session);
                });
    }

    @Transactional
    public void saveSession(BotSession session) {
        sessionRepository.save(session);
    }

    @Transactional
    public void setSessionState(Long chatId, BotState state) {
        BotSession session = getSession(chatId);
        session.setState(state);
        sessionRepository.save(session);
    }

    @Transactional
    public void clearSession(Long chatId) {
        sessionRepository.deleteById(chatId);
    }
}

