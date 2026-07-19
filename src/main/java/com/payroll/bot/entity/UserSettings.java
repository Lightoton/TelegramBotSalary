package com.payroll.bot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {

    @Id
    @Column(name = "chat_id")
    private Long chatId;



    @Column(name = "hourly_rate", nullable = false)
    private BigDecimal hourlyRate = BigDecimal.ZERO;

    @Column(name = "monthly_tax", nullable = false)
    private BigDecimal monthlyTax = BigDecimal.ZERO;

    @Column(name = "rent_limit", nullable = false)
    private BigDecimal rentLimit = new BigDecimal("-1");

    @Column(name = "food_limit", nullable = false)
    private BigDecimal foodLimit = new BigDecimal("-1");

    @Column(name = "additional_limit", nullable = false)
    private BigDecimal additionalLimit = new BigDecimal("-1");
}
