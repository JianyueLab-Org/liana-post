package com.liana.post.syncer.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liana.post.syncer.model.entity.OutboxMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxMessageMapper extends BaseMapper<OutboxMessageEntity> {
}