package com.payroll.bot.repository;

import com.payroll.bot.entity.BotSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BotSessionRepository extends JpaRepository<BotSession, Long> {
}
