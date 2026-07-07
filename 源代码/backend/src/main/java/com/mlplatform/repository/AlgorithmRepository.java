package com.mlplatform.repository;

import com.mlplatform.model.Algorithm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface AlgorithmRepository {

    @Select("SELECT * FROM algorithm")
    List<Algorithm> findAll();

    @Select("SELECT * FROM algorithm WHERE id = #{id}")
    Algorithm findById(Long id);

    @Select("SELECT * FROM algorithm WHERE category = #{category}")
    List<Algorithm> findByCategory(String category);

    @Select("SELECT * FROM algorithm WHERE name = #{name}")
    Algorithm findByName(String name);

    @Insert("INSERT INTO algorithm (name, display_name, description, category, type, parameters) VALUES (#{name}, #{displayName}, #{description}, #{category}, #{type}, #{parameters})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Algorithm algorithm);

    @Update("UPDATE algorithm SET display_name=#{displayName}, description=#{description}, parameters=#{parameters} WHERE id=#{id}")
    int update(Algorithm algorithm);

    @Delete("DELETE FROM algorithm WHERE id = #{id}")
    int deleteById(Long id);
}