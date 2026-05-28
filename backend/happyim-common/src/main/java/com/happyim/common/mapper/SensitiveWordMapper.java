package com.happyim.common.mapper;

import com.happyim.common.model.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SensitiveWordMapper {
    List<SensitiveWord> findAll();
    List<String> findAllWords();
    SensitiveWord findByWord(@Param("word") String word);
    int insert(@Param("word") String word);
    int deleteById(@Param("id") Long id);
}
