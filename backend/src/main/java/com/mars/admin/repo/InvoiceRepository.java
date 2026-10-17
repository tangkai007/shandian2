package com.mars.admin.repo;

import com.mars.admin.model.Invoice;

import java.util.List;
import java.util.Optional;

/** 发票仓储（表 {@code invoice}），契约 §4.9 API-ADM-28。 */
public interface InvoiceRepository {

    List<Invoice> listAll();

    Optional<Invoice> findById(String invoiceId);

    void save(Invoice invoice);
}
