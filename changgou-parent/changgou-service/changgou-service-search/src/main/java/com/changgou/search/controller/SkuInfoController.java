package com.changgou.search.controller;

import com.changgou.search.service.SkuInfoService;
import entity.Result;
import entity.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/search")
@CrossOrigin   //解决跨域访问
public class SkuInfoController {
    @Autowired
    private SkuInfoService skuInfoService;
    @GetMapping
    public Result<Map<String,Object>> search(@RequestParam(required = false) Map<String,String> searchMap){
        return new Result<>(true,StatusCode.OK,"查询成功",skuInfoService.search(searchMap));
    }
    @GetMapping("/import")
    public Result importData(){
        skuInfoService.importData();
        return new Result(true, StatusCode.OK,"导入索引库成功");
    }
}
