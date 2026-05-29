package com.happyim.common.mapper;

import com.happyim.common.model.entity.SystemAnnouncement;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SystemAnnouncementMapper {
    int insert(SystemAnnouncement ann);
    List<SystemAnnouncement> findAll();
    int deleteById(Long id);
}
