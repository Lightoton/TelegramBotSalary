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

    @Transactional
    public BotSession getSession(Long chatId) {
        return sessionRepository.findById(chatId)
                .orElseGet(() -> sessionRepository.save(new BotSession(chatId)));
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

