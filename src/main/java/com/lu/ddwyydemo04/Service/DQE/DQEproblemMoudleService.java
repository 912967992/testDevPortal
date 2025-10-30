package com.lu.ddwyydemo04.Service.DQE;

import com.lu.ddwyydemo04.dao.DQE.DQEDao;
import com.lu.ddwyydemo04.pojo.Samples;
import com.lu.ddwyydemo04.pojo.TestIssues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DQEproblemMoudleService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DQEDao dqeDao;

    public List<TestIssues> searchTestissues(){
        return dqeDao.searchTestissues();
    }
    public List<TestIssues> selectTestIssues(List<Integer> selectedIds){
        return dqeDao.selectTestIssues(selectedIds);
    }

    public List<TestIssues> selectTestIssuesFromSampleid(int sampleId){
        return dqeDao.selectTestIssuesFromSampleid(sampleId);
    }


    public List<Samples> searchSamplesDQE(){
        return dqeDao.searchSamplesDQE();
    }

    public List<Map<String, Object>> addNewRow(int sampleId){
        return dqeDao.addNewRow(sampleId);
    }

}
