package com.changgou.search.controller;

import com.changgou.search.feign.SkuFeign;
import entity.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
@RequestMapping("/search")
public class SkuController {
    @Autowired
    private SkuFeign skuFeign;

    @GetMapping("/list")
    public String search(@RequestParam(required = false) Map<String,String> searchMap, Model model){
        Result<Map<String, Object>> searchResult = skuFeign.search(searchMap);
        Map<String, Object> dataMap = searchResult.getData();
        model.addAttribute("result",dataMap);
        model.addAttribute("searchMap",searchMap);
        //获取上次请求地址
        String url=url(searchMap);
        model.addAttribute("url",url);
        return "search";
    }

    /**
     * 拼接组装用户请求的url地址
     */
    public String url(Map<String,String> searchMap){
        String url="/search/list"; //初始化地址
        if(searchMap!=null&&searchMap.size()>0){
            url+="?";
            for(Map.Entry<String,String> entry:searchMap.entrySet()){
                String key = entry.getKey();
                String value = entry.getValue();
                url+=key+"="+value+"&";
            }
            //去掉最后一个&
            url=url.substring(0,url.length()-1);
        }
        return url;
    }
}
