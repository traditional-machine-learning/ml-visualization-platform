package com.mlplatform.repository;

import com.mlplatform.model.Dataset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface DatasetRepository {

    @Select("SELECT * FROM dataset")
    List<Dataset> findAll();

    @Select("SELECT * FROM dataset WHERE id = #{id}")
    Dataset findById(Long id);

    @Select("SELECT * FROM dataset WHERE name = #{name}")
    Dataset findByName(String name);

    @Select("SELECT * FROM dataset WHERE category = #{category}")
    List<Dataset> findByCategory(String category);

    @Insert("INSERT INTO dataset (name, description, category, feature_count, sample_count, features, data_content) VALUES (#{name}, #{description}, #{category}, #{featureCount}, #{sampleCount}, #{features}, #{dataContent})")
    int insert(Dataset dataset);

    @Update("UPDATE dataset SET name=#{name}, description=#{description}, feature_count=#{featureCount}, sample_count=#{sampleCount}, features=#{features}, data_content=#{dataContent} WHERE id=#{id}")
    int update(Dataset dataset);

    @Delete("DELETE FROM dataset WHERE id = #{id}")
    int deleteById(Long id);
}