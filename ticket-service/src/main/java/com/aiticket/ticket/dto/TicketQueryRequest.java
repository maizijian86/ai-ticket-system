package com.aiticket.ticket.dto;

import com.aiticket.common.enums.Priority;
import com.aiticket.common.enums.TicketCategory;
import com.aiticket.common.enums.TicketStatus;
import lombok.Data;

import java.util.List;

@Data
public class TicketQueryRequest {
    private Long creatorId;
    private Long excludeCreatorId;
    private Long handlerId;
    private TicketStatus status;

    /**
     * 多状态筛选，与 status 互斥（statuses 优先，见 TicketService.buildSpecification）。
     *
     * 格式用**逗号拼接的字符串**：statuses=ACCEPTED,REJECTED。这也是前端
     * （src/api/ticket.ts 的 listTickets 调用方）实际使用的形式，实测可行。
     *
     * 关于 statuses[] 数组形式：本类是隐式参数绑定，实测两种结果都有 ——
     *   - axios 的默认序列化会把方括号转义成 %5B%5D，Spring 反而能把它绑到
     *     statuses 上（实测 statuses%5B%5D=COMPLETED 能正确筛出 2 条）；
     *   - 但**手写**的、方括号未转义的 ?statuses[]=A 会被 Tomcat 直接拒掉，
     *     返回一个 HTML 的 HTTP 400（不是本项目那套 Result 信封），前端拦截器
     *     看到的就是一个解析不了的错误。
     *
     * 所以：能传逗号串就传逗号串。不要去依赖 statuses[] —— 它能不能用取决于
     * 客户端怎么序列化，是个不该押注的隐式行为。
     */
    private List<TicketStatus> statuses;

    private TicketCategory category;
    private Priority priority;
    private String keyword;

    /**
     * true = 跳过「只查大厅可见工单」那条谓词（isVisible=true OR IS NULL）。
     *
     * 注意它**只管可见性**，不管归属。此前它还被用来关掉 listTickets 里那段
     * 「ACCEPTED/COMPLETED 自动按 handlerId 收窄」的逻辑，两个毫不相干的语义
     * 叠在同一个开关上：传了 includeAll 想查隐藏工单，会顺带把范围从"我的"
     * 放大成"全站的"。该耦合已拆掉，归属请显式传 handlerId/creatorId。
     */
    private Boolean includeAll;

    private int page = 1;
    private int pageSize = 10;
}
