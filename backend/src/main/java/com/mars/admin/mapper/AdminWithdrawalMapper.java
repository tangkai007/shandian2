package com.mars.admin.mapper;

import com.mars.admin.model.AdminWithdrawalView;
import com.mars.admin.repo.AdminWithdrawalQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 财务提现 Mapper（{@code withdrawal} ⋈ {@code user} ⋈ {@code user_auth}）。
 *
 * <p>契约 §4.9 API-ADM-24/25/26；字段名映射见 §5.3.1 B。{@code period} 为空时按
 * {@code created_at} 归月（{@code DATE_FORMAT('%Y-%m')}）回退。
 */
@Mapper
public interface AdminWithdrawalMapper extends AdminWithdrawalQuery {

    String COLUMNS = "w.order_no AS orderNo, "
            + "COALESCE(w.period, DATE_FORMAT(w.created_at, '%Y-%m')) AS period, "
            + "COALESCE(u.nickname, '') AS anchorName, COALESCE(ua.real_name, '') AS realName, "
            + "w.amount_fen AS preTaxAmount, w.tax_fen AS taxWithheld, "
            + "CASE WHEN w.net_amount_fen = 0 THEN w.amount_fen - w.tax_fen ELSE w.net_amount_fen END AS netAmount, "
            + "w.bank_account_masked AS bankAccountMasked, w.bank_name_match AS bankNameMatch, "
            + "w.status AS status, w.reject_reason AS statusNote, w.paid_at AS paidAt";

    String FROM = " FROM `withdrawal` w "
            + "LEFT JOIN `user` u ON u.id = w.anchor_id "
            + "LEFT JOIN `user_auth` ua ON ua.user_id = w.anchor_id ";

    @Override
    @Select("<script>SELECT " + COLUMNS + FROM
            + "<where><if test='period != null and period != \"\"'>"
            + "COALESCE(w.period, DATE_FORMAT(w.created_at, '%Y-%m')) = #{period}"
            + "</if></where> ORDER BY w.created_at DESC, w.id DESC</script>")
    List<AdminWithdrawalView> list(@Param("period") String period);

    @Override
    @Select("SELECT " + COLUMNS + FROM + "WHERE w.order_no = #{orderNo}")
    Optional<AdminWithdrawalView> find(@Param("orderNo") String orderNo);
}
