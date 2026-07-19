package com.payroll.bot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bot_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BotUser {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;
}
