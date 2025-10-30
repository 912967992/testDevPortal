package com.lu.ddwyydemo04.dao;

import com.lu.ddwyydemo04.pojo.FileData;
import com.lu.ddwyydemo04.pojo.Samples;
import com.lu.ddwyydemo04.pojo.TestIssues;
import com.lu.ddwyydemo04.pojo.TotalData;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
@Repository
public interface TestManDao {

    public List<Samples> getTestManPanel(@Param("tester") String tester);

    public int queryCountTotal(@Param("name")String name);

    public void createTotal(TotalData totalData);

    public void updateTotal(@Param("name")String name);

    public List<Samples> searchSamples(@Param("tester") String tester,@Param("keyword") String keyword);

    public List<Samples> searchSamplesByAsc(@Param("tester") String tester,@Param("keyword") String keyword);

    public List<Samples> searchSamplesByDesc(@Param("tester") String tester,@Param("keyword") String keyword);

    public void updateSample(Samples sample);

    public void updateSampleTeamWork(Samples sample);

    public void finishTest(@Param("sample_schedule")String sample_schedule,@Param("sample_id")int sample_id);

    public void finishTestWithoutTime(@Param("sample_schedule")String sample_schedule,@Param("finish_time")String finish_time,
                                      @Param("sample_id")int sample_id);


    public LocalDateTime  queryCreateTime(@Param("sample_id")int sample_id);
    public String querySample_name(int sample_id);

    public String queryFilepath(Samples sample);

    public String queryTester_teamwork(int sample_id);

    public String queryTester(int sample_id);

    public int deleteFromTestIssues(int sample_id);//根据文件删除sample数据库的数据
    public int deleteFromSamples(int sample_id);//根据文件删除sample数据库的数据

    //提取问题点相关
    public int querySampleId(String filepath);//根据文件删除sample数据库的数据

    public int insertTestIssues(TestIssues testIssues);


    public int queryHistoryid(@Param("sample_id") int sample_id);
    public int setDuration(@Param("planTestDuration") double planTestDuration,@Param("testDuration") double testDuration,
                           @Param("sample_id") int sample_id);


    public LocalDateTime  queryPlanFinishTime(@Param("sample_id")int sample_id);


    public List<Samples> searchSampleTestMan(@Param("keyword") String keyword,
                                             @Param("problemTimeStart")String problemTimeStart,
                                             @Param("problemTimeEnd")String problemTimeEnd,
                                             @Param("problemFinishStart")String problemFinishStart,
                                             @Param("problemFinishEnd")String problemFinishEnd,
                                             @Param("sample_schedule")String sample_schedule);


}
