package com.happyim.common.mapper;

import com.happyim.common.model.entity.User;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    int insert(User user);

    User findByEmail(@Param("email") String email);

    User findByUsername(@Param("username") String username);

    User findById(@Param("id") Long id);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    int updateLoginInfo(@Param("id") Long id, @Param("ip") String ip);

    List<User> searchByKeyword(@Param("keyword") String keyword);

    List<User> findByIds(@Param("ids") List<Long> ids);

    int updateProfile(User user);

    // admin
    List<User> findUsersPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);
    int countUsers(@Param("keyword") String keyword);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    int countTodayNewUsers();
    int countTotalUsers();
}
