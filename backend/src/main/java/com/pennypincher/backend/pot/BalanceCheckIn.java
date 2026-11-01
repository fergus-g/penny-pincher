package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.time.YearMonth;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.pennypincher.backend.entry.YearMonthConverter;

/**
 * A Pot's actual balance at the end of a month, entered by hand. Later months are calculated
 * from it; that month's own Contributions are already in it.
 */
@Entity
@Table(name = "balance_check_ins")
public class BalanceCheckIn {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "pot_id", nullable = false)
	private Long potId;

	@Convert(converter = YearMonthConverter.class)
	@Column(nullable = false)
	private YearMonth month;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal balance;

	protected BalanceCheckIn() {
	}

	public BalanceCheckIn(Long potId, YearMonth month, BigDecimal balance) {
		this.potId = potId;
		this.month = month;
		this.balance = balance;
	}

	public Long getPotId() {
		return this.potId;
	}

	public YearMonth getMonth() {
		return this.month;
	}

	public BigDecimal getBalance() {
		return this.balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

}
