package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.RecruitmentAiCostReservation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RecruitmentAiCostReservationMapper extends BaseMapper<RecruitmentAiCostReservation> {
    @Update("""
            UPDATE recruitment_ai_cost_reservation
            SET status = 'RELEASED', settled_at = CURRENT_TIMESTAMP(3)
            WHERE status = 'RESERVED' AND created_at < #{cutoff}
            """)
    int releaseExpired(@Param("cutoff") LocalDateTime cutoff);

    @Select("""
            <script>
            SELECT COALESCE(SUM(CASE WHEN status = 'SETTLED' THEN COALESCE(actual_cost_usd, estimated_cost_usd)
                                     ELSE estimated_cost_usd END), 0)
            FROM recruitment_ai_cost_reservation
            WHERE status IN ('RESERVED', 'SETTLED') AND created_at &gt;= #{from}
            <if test="companyId != null">AND company_id = #{companyId}</if>
            </script>
            """)
    BigDecimal sumActiveCostSince(@Param("companyId") Long companyId, @Param("from") LocalDateTime from);
}
