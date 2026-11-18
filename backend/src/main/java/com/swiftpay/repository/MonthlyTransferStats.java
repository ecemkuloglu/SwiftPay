package com.swiftpay.repository;

import java.math.BigDecimal;

/** One row of the monthly statistics query; months without transfers are not returned. */
public interface MonthlyTransferStats {

    /** Month in the business time zone, formatted yyyy-MM. */
    String getMonth();

    long getTransferCount();

    BigDecimal getTotalAmount();
}
