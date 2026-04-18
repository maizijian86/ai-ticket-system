package com.aiticket.common.enums;

public enum TicketStatus {
    OPEN,               // 待接单
    ACCEPTED,           // 已接单
    PENDING_APPROVAL,   // 待审批
    COMPLETED,          // 已完成
    REJECTED,           // 已拒绝
    CLOSED              // 已关闭
}
