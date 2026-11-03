package com.pennypincher.backend.entry;

import java.time.Instant;
import java.time.YearMonth;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

/**
 * A record that an AIB Expense has gone out in a month, ticked by hand after checking the bank
 * (ADR 0003).
 */
@Entity
@Table(name = "payments")
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "entry_id", nullable = false)
	private Long entryId;

	@Convert(converter = YearMonthConverter.class)
	@Column(nullable = false)
	private YearMonth month;

	@CreationTimestamp
	@Column(name = "paid_at", nullable = false, updatable = false)
	private Instant paidAt;

	protected Payment() {
	}

	public Payment(Long entryId, YearMonth month) {
		this.entryId = entryId;
		this.month = month;
	}

	public Long getId() {
		return this.id;
	}

	public Long getEntryId() {
		return this.entryId;
	}

	public YearMonth getMonth() {
		return this.month;
	}

	public Instant getPaidAt() {
		return this.paidAt;
	}

}
