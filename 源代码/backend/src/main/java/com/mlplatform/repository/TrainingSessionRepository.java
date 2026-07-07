package com.mlplatform.repository;

import com.mlplatform.model.TrainingSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface TrainingSessionRepository {

    @Select("SELECT * FROM training_session")
    List<TrainingSession> findAll();

    @Select("SELECT * FROM training_session WHERE id = #{id}")
    TrainingSession findById(Long id);

    @Select("SELECT * FROM training_session WHERE status = #{status}")
    List<TrainingSession> findByStatus(String status);

    @Insert("INSERT INTO training_session (algorithm_id, dataset_id, parameters, status, current_step, total_steps, metrics, model_data) VALUES (#{algorithmId}, #{datasetId}, #{parameters}, #{status}, #{currentStep}, #{totalSteps}, #{metrics}, #{modelData})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TrainingSession session);

    @Update("UPDATE training_session SET status=#{status}, current_step=#{currentStep}, total_steps=#{totalSteps}, metrics=#{metrics}, model_data=#{modelData}, updated_at=NOW() WHERE id=#{id}")
    int update(TrainingSession session);

    @Delete("DELETE FROM training_session WHERE id = #{id}")
    int deleteById(Long id);
}
