package com.happyim.common.mapper;

import com.happyim.common.model.entity.Friend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FriendMapper {

    int insert(Friend friend);

    Friend findByPair(@Param("userId") Long userId, @Param("friendId") Long friendId);

    List<Friend> findByUserId(@Param("userId") Long userId);

    int deleteByPair(@Param("userId") Long userId, @Param("friendId") Long friendId);

    int updateStarred(@Param("userId") Long userId, @Param("friendId") Long friendId, @Param("isStarred") Integer isStarred);

    int updateRemark(@Param("userId") Long userId, @Param("friendId") Long friendId, @Param("remark") String remark);

    List<Friend> findByUserIdAndFriendIds(@Param("userId") Long userId, @Param("friendIds") List<Long> friendIds);
}
