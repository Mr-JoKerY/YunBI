package com.hyw.project.mapper;

import com.hyw.project.model.entity.Chart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;
import java.util.Map;

/**
* @author 17799
* @description 针对表【chart(图表信息表)】的数据库操作Mapper
* @createDate 2023-07-01 20:52:58
* @Entity com.hyw.project.model.entity.Chart
*/
public interface ChartMapper extends BaseMapper<Chart> {

    List<Map<String, Object>> queryChartData(String querySql);
}




