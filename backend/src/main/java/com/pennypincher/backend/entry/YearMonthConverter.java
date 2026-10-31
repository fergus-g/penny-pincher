package com.pennypincher.backend.entry;

import java.time.LocalDate;
import java.time.YearMonth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link YearMonth} as a DATE on the 1st of that month.
 */
@Converter
public class YearMonthConverter implements AttributeConverter<YearMonth, LocalDate> {

	@Override
	public LocalDate convertToDatabaseColumn(YearMonth month) {
		return (month != null) ? month.atDay(1) : null;
	}

	@Override
	public YearMonth convertToEntityAttribute(LocalDate date) {
		return (date != null) ? YearMonth.from(date) : null;
	}

}
