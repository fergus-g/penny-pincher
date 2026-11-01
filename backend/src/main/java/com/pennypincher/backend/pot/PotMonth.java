package com.pennypincher.backend.pot;

import java.util.List;

import com.pennypincher.backend.allocation.AllocationSummary;

/**
 * What one month's summary needs from Pots and the Allocation.
 *
 * @param pots each Pot in use that month, with its end-of-month balance
 * @param allocation the month's split of the Remainder; null with no Allocation in force
 */
public record PotMonth(List<PotBalance> pots, AllocationSummary allocation) {
}
