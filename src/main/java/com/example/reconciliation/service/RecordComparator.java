package com.example.reconciliation.service;

import com.example.reconciliation.model.RecordMatch;
import com.example.reconciliation.model.ReconciliationResult;
import com.example.reconciliation.model.ReconciliationResult.Status;
import com.example.reconciliation.model.ReconciliationType;
import com.example.reconciliation.model.SourceRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reconciles each DBS/Quantifeed pair with three checks in order, stopping at the first one that finds a difference:
 * POSITION (securityId, quantity), then TRANSACTION (side, price, amount, currency, settlementDate), then CASH (balance).
 * Numbers are compared by value (100.50 equals 100.5).
 */
@Component
public class RecordComparator {

    public ReconciliationResult compare(RecordMatch match) {
        SourceRecord dbs = match.dbs();
        SourceRecord qf = match.quantifeed();

        if (dbs == null) {
            return new ReconciliationResult(null, qf, Status.BREAK, null, "MISSING_IN_DBS", "");
        }
        if (qf == null) {
            return new ReconciliationResult(dbs, null, Status.BREAK, null, "MISSING_IN_QUANTIFEED", "");
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
