package com.liana.post.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liana.post.dispatch.model.entity.DispatchBagEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface DispatchBagMapper extends BaseMapper<DispatchBagEntity> {
    @Update("UPDATE dispatch_bag SET transport_task_code = #{taskCode}, updated_at = NOW() WHERE id = #{id} AND (transport_task_code IS NULL OR transport_task_code = #{taskCode})")
    int linkIfUnassigned(@Param("id") Long id, @Param("taskCode") String taskCode);
}
