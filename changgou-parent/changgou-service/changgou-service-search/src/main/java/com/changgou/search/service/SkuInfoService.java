package com.changgou.search.service;

import java.util.Map;

public interface SkuInfoService {
    void importData();

    Map<String,Object> search(Map<String,String> map);
}
