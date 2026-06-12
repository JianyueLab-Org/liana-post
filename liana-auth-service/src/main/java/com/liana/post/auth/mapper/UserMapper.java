package com.liana.post.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liana.post.auth.model.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}