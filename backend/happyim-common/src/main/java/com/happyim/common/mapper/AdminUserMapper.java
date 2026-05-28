package com.happyim.common.mapper;

import com.happyim.common.model.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminUserMapper {
    AdminUser findByUsername(@Param("username") String username);
    AdminUser findById(@Param("id") Long id);
    int countAll();
    int insert(AdminUser adminUser);
}
