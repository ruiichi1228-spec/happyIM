package com.happyim.common.mapper;

import com.happyim.common.model.entity.Blacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BlacklistMapper {

    int insert(Blacklist blacklist);

    Blacklist findByPair(@Param("userId") Long userId, @Param("blockedUserId") Long blockedUserId);

    List<Blacklist> findByUserId(@Param("userId") Long userId);

    int deleteByPair(@Param("userId") Long userId, @Param("blockedUserId") Long blockedUserId);
}
