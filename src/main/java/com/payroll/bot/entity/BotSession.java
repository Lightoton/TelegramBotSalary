package com.payroll.bot.entity;

import com.payroll.bot.telegram.BotState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bot_session")
@Getter
@Setter
public class BotSession {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Enumerated(EnumType.STRING)
    private BotState state;

    private LocalDate tempDate;
    private String tempServiceType;
    private Long tempTransactionId;
    private String tempServiceName;
    private Long tempServiceId;
    private String tempCategoryName;

    private Integer anchorMessageId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bot_session_recent_messages", joinColumns = @JoinColumn(name = "chat_id"))
    @Column(name = "message_id")
    private List<Integer> recentMessageIds = new ArrayList<>();

    public BotSession() {
        this.state = BotState.AWAITING_PASSWORD;
    }

    public BotSession(Long chatId) {
        this.chatId = chatId;
        this.state = BotState.AWAITING_PASSWORD;
    }
}
