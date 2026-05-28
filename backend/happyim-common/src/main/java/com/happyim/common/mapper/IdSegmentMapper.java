package com.happyim.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IdSegmentMapper {

    @Select("SELECT max_id FROM id_segment WHERE biz_tag = #{bizTag}")
    Long getMaxId(@Param("bizTag") String bizTag);

    @Update("UPDATE id_segment SET max_id = max_id + #{step} WHERE biz_tag = #{bizTag}")
    int incrementMaxId(@Param("bizTag") String bizTag, @Param("step") int step);
}
