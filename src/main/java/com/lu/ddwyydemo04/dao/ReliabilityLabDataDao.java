package com.lu.ddwyydemo04.dao;

import com.lu.ddwyydemo04.pojo.ReliabilityLabData;

public interface ReliabilityLabDataDao {
    int insert(ReliabilityLabData data);
    ReliabilityLabData selectLatest();
}


