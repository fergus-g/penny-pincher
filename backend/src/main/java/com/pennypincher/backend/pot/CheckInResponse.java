package com.pennypincher.backend.pot;

import java.math.BigDecimal;
import java.time.YearMonth;

public record CheckInResponse(YearMonth month, BigDecimal balance) {

	static CheckInResponse from(BalanceCheckIn checkIn) {
		return new CheckInResponse(checkIn.getMonth(), checkIn.getBalance());
	}

}
