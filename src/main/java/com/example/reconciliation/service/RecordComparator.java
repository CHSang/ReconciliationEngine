package com.example.reconciliation.service;

import com.example.reconciliation.model.RecordMatch;
import com.example.reconciliation.model.ReconciliationResult;
import com.example.reconciliation.model.ReconciliationResult.Status;
import com.example.reconciliation.model.ReconciliationType;
import com.example.reconciliation.model.SourceRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reconciles each DBS/Quantifeed pair with three checks in order, stopping at the first one that finds a difference:
 * POSITION (securityId, quantity), then TRANSACTION (side, price, amount, currency, settlementDate), then CASH (balance).
 * Numbers are compared by value (100.50 equals 100.5).
 * A record found in only one system is PENDING (AWAITING_SETTLEMENT) until its businessDate + settlement-days,
 * because the other system may only book it at settlement (e.g. T+2). After that it is a MISSING_IN_... break.
 */
public class RecordComparator {

    private final int settlementDays;
    private final LocalDate runDate;

    public RecordComparator(int settlementDays, LocalDate runDate) {
        this.settlementDays = settlementDays;
        this.runDate = runDate;
    }

    public ReconciliationResult compare(RecordMatch match) {
        SourceRecord dbs = match.dbs();
        SourceRecord qf = match.quantifeed();

        if (dbs == null) {
            return missing(null, qf, "MISSING_IN_DBS");
        }
        if (qf == null) {
            return missing(dbs, null, "MISSING_IN_QUANTIFEED");
        }

        List<String> details = new ArrayList<>();
        String breakType = null;

        breakType = check(details, breakType, "securityId", "SECURITY_MISMATCH", dbs.securityId(), qf.securityId());
        breakType = check(details, breakType, "quantity", "QUANTITY_MISMATCH", dbs.quantity(), qf.quantity());
        if (breakType != null) {
            return breakResult(dbs, qf, ReconciliationType.POSITION, breakType, details);
        }

        breakType = check(details, breakType, "side", "SIDE_MISMATCH", dbs.side(), qf.side());
        breakType = check(details, breakType, "price", "PRICE_MISMATCH", dbs.price(), qf.price());
        breakType = check(details, breakType, "amount", "AMOUNT_MISMATCH", dbs.amount(), qf.amount());
        breakType = check(details, breakType, "currency", "CURRENCY_MISMATCH", dbs.currency(), qf.currency());
        breakType = check(details, breakType, "settlementDate", "SETTLEMENT_DATE_MISMATCH", dbs.settlementDate(), qf.settlementDate());
        if (breakType != null) {
            return breakResult(dbs, qf, ReconciliationType.TRANSACTION, breakType, details);
        }

        breakType = check(details, breakType, "balance", "CASH_BALANCE_MISMATCH", dbs.balance(), qf.balance());
        if (breakType != null) {
            return breakResult(dbs, qf, ReconciliationType.CASH, breakType, details);
        }

        return new ReconciliationResult(dbs, qf, Status.MATCHED, null, null, "");
    }

    /** One-sided record: PENDING while still inside the settlement window, otherwise a BREAK. */
    private ReconciliationResult missing(SourceRecord dbs, SourceRecord qf, String breakType) {
        SourceRecord record = dbs != null ? dbs : qf;
        LocalDate expectedBy = record.businessDate() == null ? null : record.businessDate().plusDays(settlementDays);
        if (expectedBy != null && expectedBy.isAfter(runDate)) {
            return new ReconciliationResult(dbs, qf, Status.PENDING, null, "AWAITING_SETTLEMENT", "expected by " + expectedBy);
        }
        return new ReconciliationResult(dbs, qf, Status.BREAK, null, breakType, "");
    }

    private static ReconciliationResult breakResult(SourceRecord dbs, SourceRecord qf, ReconciliationType type,
                                                    String breakType, List<String> details) {
        return new ReconciliationResult(dbs, qf, Status.BREAK, type, breakType, String.join("; ", details));
    }

    /** Adds a detail if the values differ; returns the breakType to keep (the first one found wins). */
    private static String check(List<String> details, String breakType, String field, String fieldBreakType, Object dbs, Object qf) {
        boolean same = dbs instanceof BigDecimal a && qf instanceof BigDecimal b
                ? a.compareTo(b) == 0
                : Objects.equals(dbs, qf);
        if (same) {
            return breakType;
        }
        details.add(field + ": " + text(dbs) + " vs " + text(qf));
        return breakType != null ? breakType : fieldBreakType;
    }

    /** Numbers without trailing zeros: 25.50 -> 25.5. */
    private static String text(Object value) {
        return value instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : String.valueOf(value);
    }
}
