package com.pennypincher.backend.entry;

import java.math.BigDecimal;
import java.time.Instant;
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
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "entries")
public class Entry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private EntryType type;

	@Column(nullable = false, length = 50)
	private String category;

	@Column(length = 100)
	private String name;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Convert(converter = YearMonthConverter.class)
	@Column(name = "start_month", nullable = false)
	private YearMonth startMonth;

	@Convert(converter = YearMonthConverter.class)
	@Column(name = "end_month")
	private YearMonth endMonth;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Entry() {
	}

	public Entry(EntryType type, String category, String name, BigDecimal amount, YearMonth startMonth,
			YearMonth endMonth) {
		this.type = type;
		this.category = category;
		this.name = name;
		this.amount = amount;
		this.startMonth = startMonth;
		this.endMonth = endMonth;
	}

	public Long getId() {
		return this.id;
	}

	public EntryType getType() {
		return this.type;
	}

	public void setType(EntryType type) {
		this.type = type;
	}

	public String getCategory() {
		return this.category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public YearMonth getStartMonth() {
		return this.startMonth;
	}

	public void setStartMonth(YearMonth startMonth) {
		this.startMonth = startMonth;
	}

	public YearMonth getEndMonth() {
		return this.endMonth;
	}

	public void setEndMonth(YearMonth endMonth) {
		this.endMonth = endMonth;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
