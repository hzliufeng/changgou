package com.changgou.content.controller;

import com.changgou.content.pojo.Content;
import com.changgou.content.service.ContentService;
import entity.Result;
import entity.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/content")
public class ContentContrller {
    @Autowired
    private ContentService contentService;
    @GetMapping("/list/category/{id}")
    public Result<List<Content>> findByCategory(@PathVariable("id") Long id){
        return new Result<>(true, StatusCode.OK,"查询成功",contentService.findByCategory(id));
    }
}
