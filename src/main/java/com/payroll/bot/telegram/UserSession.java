package com.payroll.bot.telegram;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UserSession {
    private BotState state;
    
    // Temporary variables for adding/editing income
    private LocalDate tempDate;
    private String tempServiceType;
    private Long tempTransactionId;

    private String tempServiceName;
    private Long tempServiceId;
    
    // Temporary variables for budget
    private String tempCategoryName;

    // Track recent message IDs for rolling window (max 3)
    private java.util.List<Integer> recentMessageIds = new java.util.ArrayList<>();

    // Anchor message to hold the reply keyboard
    private Integer anchorMessageId;

    public UserSession() {
        this.state = BotState.AWAITING_PASSWORD;
    }
}
