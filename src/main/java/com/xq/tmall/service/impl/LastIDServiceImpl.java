package com.xq.tmall.service.impl;

import com.xq.tmall.dao.LastIDMapper;
import com.xq.tmall.service.LastIDService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service("lastIDService")
public class LastIDServiceImpl implements LastIDService {
    private LastIDMapper lastIDMapper;

    @Resource(name = "lastIDMapper")
    public void setLastIDMapper(LastIDMapper lastIDMapper) {
        this.lastIDMapper = lastIDMapper;
    }

    @Override
    public int selectLastID() {
        return lastIDMapper.selectLastID();
    }
}
