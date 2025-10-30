package com.lu.ddwyydemo04.dao;

import com.lu.ddwyydemo04.pojo.Samples;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SamplesDao {

    public int sampleCount(@Param("model") String model,@Param("coding") String coding,@Param("category") String category,@Param("version") String version,@Param("sample_frequency")int sample_frequency,
                           @Param("big_species") String big_species , @Param("small_species") String small_species,@Param("high_frequency") String high_frequency,@Param("questStats") String questStats);

    public int sampleOtherCount(@Param("model") String model,@Param("coding") String coding,@Param("high_frequency") String high_frequency);


    public int insertSample(@Param("tester")String tester,@Param("filepath") String filepath,@Param("model") String model,@Param("coding") String coding,@Param("full_model") String full_model,
                            @Param("category") String category,@Param("version") String version,@Param("sample_name") String sample_name,
                            @Param("planfinish_time") String planfinish_time,@Param("create_time") String create_time,@Param("sample_schedule") String sample_schedule,
                            @Param("sample_frequency") int sample_frequency,@Param("sample_quantity") int sample_quantity,
                            @Param("big_species") String big_species, @Param("small_species") String small_species, @Param("high_frequency") String high_frequency,@Param("questStats") String questStats,
                            @Param("planTestDuration") double planTestDuration);

    List<String> querySample(@Param("model") String model, @Param("coding") String coding, @Param("high_frequency") String high_frequency);

    public String getUUID(@Param("filepath") String filepath);

    public int updateUUID(@Param("filepath") String filepath,@Param("uuid") String uuid);
}
