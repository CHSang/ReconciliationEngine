package com.example.reconciliation.service;

import com.example.reconciliation.model.RecordMatch;
import com.example.reconciliation.model.ReconciliationResult;
import com.example.reconciliation.model.ReconciliationResult.Status;
import com.example.reconciliation.model.SourceRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compares each DBS/Quantifeed pair and decides MATCHED or BREAK.
 * Numbers are compared by value (100.50 equals 100.5). If several fields differ, all are listed
 * and the first one checked sets the breakType.
 */
@Component
public class RecordComparator {

    public ReconciliationResult compare(RecordMatch match) {
        SourceRecord dbs = match.dbs();
        SourceRecord qf = match.quantifeed();

        if (dbs == null) {
            return new ReconciliationResult(null, qf, Status.BREAK, "MISSING_IN_DBS", null, mainValue(qf), null, "");
        }
        if (qf == null) {
            return new ReconciliationResult(dbs, null, Status.BREAK, "MISSING_IN_QUANTIFEED", mainValue(dbs), null, null, "");
        }

        List<String> details = new ArrayList<>();
        String breakType = null;

        switch (dbs.type()) {
            case POSITION -> {
                breakType = check(details, breakType, "securityId", "SECURITY_MISMATCH", dbs.securityId(), qf.securityId());
                breakType = check(details, breakType, "quantity", "QUANTITY_MISMATCH", dbs.quantity(), qf.quantity());
            }
            case TRANSACTION -> {
                breakType = check(details, breakType, "securityId", "SECURITY_MISMATCH", dbs.securityId(), qf.securityId());
                breakType = check(details, breakType, "side", "SIDE_MISMATCH", dbs.side(), qf.side());
                breakType = check(details, breakType, "quantity", "QUANTITY_MISMATCH", dbs.quantity(), qf.quantity());
                breakType = check(details, breakType, "price", "PRICE_MISMATCH", dbs.price(), qf.price());
                breakType = check(details, breakType, "amount", "AMOUNT_MISMATCH", dbs.amount(), qf.amount());
                breakType = check(details, breakType, "currency", "CURRENCY_MISMATCH", dbs.currency(), qf.currency());
                breakType = check(details, breakType, "settlementDate", "SETTLEMENT_DATE_MISMATCH", dbs.settlementDate(), qf.settlementDate());
            }
            case CASH -> {
                breakType = check(details, breakType, "currency", "CURRENCY_MISMATCH", dbs.currency(), qf.currency());
                breakType = check(details, breakType, "balance", "CASH_BALANCE_MISMATCH", dbs.balance(), qf.balance());
            }
        }

        Status status = breakType == null ? Status.MATCHED : Status.BREAK;
        BigDecimal dbsValue = mainValue(dbs);
        BigDecimal qfValue = mainValue(qf);
        return new ReconciliationResult(dbs, qf, status, breakType, dbsValue, qfValue,
                dbsValue.subtract(qfValue), String.join("; ", details));
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

    /** Same number format as the CSV value columns: 25.50 -> 25.5. */
    private static String text(Object value) {
        return value instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : String.valueOf(value);
    }

    /** The value shown in the dbsValue / quantifeedValue / difference columns. */
    private static BigDecimal mainValue(SourceRecord r) {
        return switch (r.type()) {
            case POSITION -> r.quantity();
            case TRANSACTION -> r.amount();
            case CASH -> r.balance();
        };
    }
}
