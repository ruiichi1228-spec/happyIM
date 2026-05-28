package com.happyim.common.mapper;

import com.happyim.common.model.entity.FriendRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FriendRequestMapper {

    int insert(FriendRequest req);

    FriendRequest findPending(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId);

    List<FriendRequest> findByToUserId(@Param("toUserId") Long toUserId);

    FriendRequest findById(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
