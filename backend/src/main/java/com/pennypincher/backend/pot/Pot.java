package com.pennypincher.backend.pot;

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
 * A running balance of money saved or owed. Amounts are always stored positive; a Debt Pot is
 * only shown negative.
 */
@Entity
@Table(name = "pots")
public class Pot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PotKind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Account account;

	/** The balance at the end of the opening month, the same rule as a Balance Check-in. */
	@Column(name = "opening_balance", nullable = false, precision = 12, scale = 2)
	private BigDecimal openingBalance;

	@Convert(converter = YearMonthConverter.class)
	@Column(name = "opening_month", nullable = false)
	private YearMonth openingMonth;

	/** Hidden from this month onwards; null while in use. */
	@Convert(converter = YearMonthConverter.class)
	@Column(name = "archived_from")
	private YearMonth archivedFrom;

	protected Pot() {
	}

	public Pot(String name, PotKind kind, Account account, BigDecimal openingBalance, YearMonth openingMonth) {
		this.name = name;
		this.kind = kind;
		this.account = account;
		this.openingBalance = openingBalance;
		this.openingMonth = openingMonth;
	}

	/** Opened by this month, and not archived yet. */
	public boolean isActiveIn(YearMonth month) {
		return !this.openingMonth.isAfter(month) && (this.archivedFrom == null || this.archivedFrom.isAfter(month));
	}

	public Long getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public PotKind getKind() {
		return this.kind;
	}

	public Account getAccount() {
		return this.account;
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	public BigDecimal getOpeningBalance() {
		return this.openingBalance;
	}

	public void setOpeningBalance(BigDecimal openingBalance) {
		this.openingBalance = openingBalance;
	}

	public YearMonth getOpeningMonth() {
		return this.openingMonth;
	}

	public void setOpeningMonth(YearMonth openingMonth) {
		this.openingMonth = openingMonth;
	}

	public YearMonth getArchivedFrom() {
		return this.archivedFrom;
	}

	public void setArchivedFrom(YearMonth archivedFrom) {
		this.archivedFrom = archivedFrom;
	}

}
