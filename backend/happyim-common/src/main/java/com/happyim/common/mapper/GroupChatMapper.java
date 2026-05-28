package com.happyim.common.mapper;

import com.happyim.common.model.entity.GroupChat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface GroupChatMapper {
    int insert(GroupChat group);
    GroupChat findById(@Param("id") Long id);
    List<GroupChat> findByMemberId(@Param("userId") Long userId);
    int updateInfo(Map<String, Object> params);
    int updateMemberCount(@Param("id") Long id, @Param("delta") int delta);
    int dissolve(@Param("id") Long id);
}
