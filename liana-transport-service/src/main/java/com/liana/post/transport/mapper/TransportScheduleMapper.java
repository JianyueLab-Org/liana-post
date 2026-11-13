package com.liana.post.transport.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import com.liana.post.transport.model.entity.TransportScheduleEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransportScheduleMapper extends BaseMapper<TransportScheduleEntity> {
    @Select("SELECT * FROM transport_schedule WHERE id = #{id} FOR UPDATE")
    TransportScheduleEntity lockById(@Param("id") Long id);
}
