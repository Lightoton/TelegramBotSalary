package com.payroll.bot.service;

import com.payroll.bot.telegram.BotState;
import com.payroll.bot.telegram.UserSession;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {
    private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();

    public UserSession getSession(Long chatId) {
        return sessions.computeIfAbsent(chatId, k -> new UserSession());
    }

    public void setSessionState(Long chatId, BotState state) {
        getSession(chatId).setState(state);
    }

    public void clearSession(Long chatId) {
        sessions.remove(chatId);
    }
}
