package com.lu.ddwyydemo04.Service;

import com.lu.ddwyydemo04.dao.QuestDao;
import com.lu.ddwyydemo04.dao.TestManDao;
import com.lu.ddwyydemo04.pojo.Samples;
import com.lu.ddwyydemo04.pojo.TestIssues;
import com.lu.ddwyydemo04.pojo.TotalData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


@Service("TestManIndexService")
public class TestManIndexService {
    @Autowired
    private QuestDao questDao;

    @Autowired
    private TestManDao testManDao;

    public Map<String, Integer> getindexPanel(String name){
        return questDao.getindexPanel(name);
    }

    public List<Samples> getTestManPanel(String tester){
        return testManDao.getTestManPanel(tester);
    }

    public int queryCountTotal(String name){ return testManDao.queryCountTotal(name);}

    public void createTotal(TotalData totalData){
        testManDao.createTotal(totalData);
    }

    public void updateTotal(String name){
        testManDao.updateTotal(name);
    }

    public List<Samples> searchSamples(String tester,String keyword){
        return testManDao.searchSamples(tester,keyword);
    }

    public List<Samples> searchSamplesByAsc(String tester,String keyword){
        return testManDao.searchSamplesByAsc(tester,keyword);
    }

    public List<Samples> searchSamplesByDesc(String tester,String keyword){
        return testManDao.searchSamplesByDesc(tester,keyword);
    }


    public void updateSample(Samples sample) {
        testManDao.updateSample(sample);
    }

    public void updateSampleTeamWork(Samples sample){
        testManDao.updateSampleTeamWork(sample);
    }

    public void finishTest(String schedule,int sample_id){
        testManDao.finishTest(schedule,sample_id);
    }
    public void finishTestWithoutTime(String schedule,String finish_time,int sample_id){

        testManDao.finishTestWithoutTime(schedule,finish_time,sample_id);
    }



    public LocalDateTime  queryCreateTime(int sample_id){
        return testManDao.queryCreateTime(sample_id);
    }

    public String queryTester_teamwork(int sample_id){
        return testManDao.queryTester_teamwork(sample_id);
    }

    public String querySample_name(int sample_id){
        return testManDao.querySample_name(sample_id);
    }

    public String queryFilepath(Samples sample){
        return testManDao.queryFilepath(sample);
    }

    public String queryTester(int sample_id){
        return testManDao.queryTester(sample_id);
    }

    public int deleteFromTestIssues(int sample_id){
        return testManDao.deleteFromTestIssues(sample_id);
    }

    public int deleteFromSamples(int sample_id){
        return testManDao.deleteFromSamples(sample_id);
    }

    //提取问题点的相关服务层
    //通过大小编码，版本，送样次数，是否高频，来返回sample_id
    public int querySampleId(String filepath){
        return testManDao.querySampleId(filepath);
    }

    public int insertTestIssues(TestIssues testIssues){
        return testManDao.insertTestIssues(testIssues);
    }

    public int queryHistoryid(int sample_id){
        return testManDao.queryHistoryid(sample_id);
    }
    public int setDuration(double planWorkDays,double workDays,int sample_id){
        return testManDao.setDuration(planWorkDays,workDays,sample_id);
    }

    public LocalDateTime  queryPlanFinishTime(int sample_id){
        return testManDao.queryPlanFinishTime(sample_id);
    }

}
