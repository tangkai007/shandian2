package com.mars.admin.mapper;

import com.mars.admin.model.Invoice;
import com.mars.admin.repo.InvoiceRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/** 发票 Mapper（表 {@code invoice}），契约 §4.9 API-ADM-28。 */
@Mapper
public interface InvoiceMapper extends InvoiceRepository {

    String COLUMNS = "invoice_id AS invoiceId, order_no AS orderNo, buyer AS buyer, "
            + "amount_fen AS amountFen, channel AS channel, status AS status, invoice_url AS invoiceUrl";

    @Override
    @Select("SELECT " + COLUMNS + " FROM `invoice` ORDER BY created_at DESC, invoice_id DESC")
    List<Invoice> listAll();

    @Override
    @Select("SELECT " + COLUMNS + " FROM `invoice` WHERE invoice_id = #{invoiceId}")
    Optional<Invoice> findById(@Param("invoiceId") String invoiceId);

    @Override
    @Insert("INSERT INTO `invoice` (invoice_id, order_no, buyer, amount_fen, channel, status, invoice_url) "
            + "VALUES (#{invoiceId}, #{orderNo}, #{buyer}, #{amountFen}, #{channel}, #{status}, "
            + "#{invoiceUrl,jdbcType=VARCHAR}) "
            + "ON DUPLICATE KEY UPDATE status = VALUES(status), invoice_url = VALUES(invoice_url), "
            + "issued_at = CASE WHEN VALUES(status) = 'ISSUED' THEN NOW(3) ELSE issued_at END")
    void save(Invoice invoice);
}
