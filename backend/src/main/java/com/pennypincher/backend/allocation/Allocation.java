package com.pennypincher.backend.allocation;

import java.math.BigDecimal;
import java.time.YearMonth;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.pennypincher.backend.entry.Account;
import com.pennypincher.backend.entry.YearMonthConverter;

/**
 * A standing plan, effective from a month until the next Allocation, for splitting the
 * Remainder into fixed Savings and Allowance, with whatever is left as Debt Repayment.
 */
@Entity
@Table(name = "allocations")
public class Allocation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Convert(converter = YearMonthConverter.class)
	@Column(name = "effective_from", nullable = false, updatable = false)
	private YearMonth effectiveFrom;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal savings;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal allowance;

	@Enumerated(EnumType.STRING)
	@Column(name = "allowance_account", nullable = false, length = 20)
	private Account allowanceAccount;

	/** Where Savings go; null leaves them unassigned. */
	@Column(name = "savings_pot_id")
	private Long savingsPotId;

	/** What Debt Repayment pays down; null leaves it unassigned. */
	@Column(name = "debt_pot_id")
	private Long debtPotId;

	protected Allocation() {
	}

	public Allocation(YearMonth effectiveFrom) {
		this.effectiveFrom = effectiveFrom;
	}

	public Long getId() {
		return this.id;
	}

	public YearMonth getEffectiveFrom() {
		return this.effectiveFrom;
	}

	public BigDecimal getSavings() {
		return this.savings;
	}

	public void setSavings(BigDecimal savings) {
		this.savings = savings;
	}

	public BigDecimal getAllowance() {
		return this.allowance;
	}

	public void setAllowance(BigDecimal allowance) {
		this.allowance = allowance;
	}

	public Account getAllowanceAccount() {
		return this.allowanceAccount;
	}

	public void setAllowanceAccount(Account allowanceAccount) {
		this.allowanceAccount = allowanceAccount;
	}

	public Long getSavingsPotId() {
		return this.savingsPotId;
	}

	public void setSavingsPotId(Long savingsPotId) {
		this.savingsPotId = savingsPotId;
	}

	public Long getDebtPotId() {
		return this.debtPotId;
	}

	public void setDebtPotId(Long debtPotId) {
		this.debtPotId = debtPotId;
	}

}
