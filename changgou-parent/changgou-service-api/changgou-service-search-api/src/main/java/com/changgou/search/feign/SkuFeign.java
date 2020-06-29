package com.changgou.search.feign;

import entity.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient("search")
@RequestMapping("/search")
public interface SkuFeign {
    @GetMapping
    Result<Map<String,Object>> search(@RequestParam(required = false) Map<String,String> searchMap);
}
