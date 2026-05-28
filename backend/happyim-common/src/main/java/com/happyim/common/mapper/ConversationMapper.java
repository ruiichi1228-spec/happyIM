package com.happyim.common.mapper;

import com.happyim.common.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConversationMapper {
    int insert(Conversation conv);
    Conversation findById(@Param("id") String id);
}
