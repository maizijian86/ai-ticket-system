package com.aiticket.ticket.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 我的钱包。前端用它做两件事：展示余额、发布/编辑工单时判断"够不够"。
 *
 * <p><b>前端的余额判断只是体验优化，最终以服务端为准</b>：前端算完可能已经过期
 *（另一个标签页刚发了一张单），服务端 {@code freezeForTicket} 会用行锁重新校验，
 * 不足就抛 402。
 *
 * <p>{@link #totalAmount} 是所有额度的合计（可用 + 冻结），只用于展示；
 * **判"能不能发这张单"必须用 {@link #availableAmount}**。
 */
@Data
@Builder
public class UserWalletDTO {

    /** 可用余额：能用来发布工单 */
    private BigDecimal availableAmount;

    /** 冻结（托管中）：已随在途工单押住 */
    private BigDecimal frozenAmount;

    /** 合计 = 可用 + 冻结 */
    private BigDecimal totalAmount;

    /**
     * 单笔充值上限，由后端下发而不是前端写死 ——
     * 这样以后调整额度不用改前端（前端只校验"不超过这个数"）。
     */
    private BigDecimal maxRecharge;

    /** 单笔充值下限（整数元，目前是 1） */
    private BigDecimal minRecharge;
}
