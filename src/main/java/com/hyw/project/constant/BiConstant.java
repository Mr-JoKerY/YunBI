package com.hyw.project.constant;

import java.util.Arrays;
import java.util.List;

/**
 * ai常量
 *
 * @author lwx
 * @since 2023/7/6 14:27
 */
public interface BiConstant {

    /**
     * AI 模型id {@link <a href="https://www.yucongming.com/">AI模型</a>}
     */
    Long BI_MODEL_ID = 1659171950288818178L;

    /**
     * 截取Ai内容字符串
     */
    String AI_SPLIT_STR = "【【【【【";
//    String AI_SPLIT_STR = "=>=>=>";

    /**
     * 限流器key
     */
    String BI_REDIS_LIMITER_KEY = "genChartByAi_";

    /**
     * 文件后缀
     */
    List<String> VALID_FILE_SUFFIX_LIST = Arrays.asList("xlsx", "csv", "xls");
}