package com.happyim.common.mapper;

import com.happyim.common.model.entity.GroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupMemberMapper {
    int insert(GroupMember member);
    int insertBatch(@Param("list") List<GroupMember> list);
    GroupMember findByGroupAndUser(@Param("groupId") Long groupId, @Param("userId") Long userId);
    List<GroupMember> findByGroupId(@Param("groupId") Long groupId);
    int deleteByGroupAndUser(@Param("groupId") Long groupId, @Param("userId") Long userId);
    int updateRole(@Param("groupId") Long groupId, @Param("userId") Long userId, @Param("role") Integer role);

    int updateGroupNickname(@Param("groupId") Long groupId, @Param("userId") Long userId, @Param("groupNickname") String groupNickname);
}
