package com.hyw.project.model.enums;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 图表状态枚举
 *
 * @author hyw
 */
@Getter
public enum ChartStatusEnum {
    WAIT("等待", 0),
    RUNNING("执行中", 1),
    FAILED("失败", 2),
    SUCCEED("成功", 3),
    RETRY("重试中", 4),
    ;

    private final String text;

    private final Integer value;

    ChartStatusEnum(String text, Integer value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取值列表
     */
    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     */
    public static ChartStatusEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (ChartStatusEnum anEnum : ChartStatusEnum.values()) {
            if (anEnum.value.intValue() == value.intValue()) {
                return anEnum;
            }
        }
        return null;
    }
}
