package com.changgou.canal.listener;

import com.alibaba.fastjson.JSON;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.changgou.content.feign.ContentFeign;
import com.changgou.content.pojo.Content;
import com.xpand.starter.canal.annotation.*;
import entity.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@CanalEventListener
public class CanalDataEventListener {
    @Autowired
    private ContentFeign contentFeign;
    @Autowired
    private StringRedisTemplate redisTemplate;
//    @InsertListenPoint
//    public void insertListener(CanalEntry.EventType eventType, CanalEntry.RowData rowData){  //eventType：监听类型，rowData:返回数据
//        List<CanalEntry.Column> list = rowData.getAfterColumnsList();
//        for (CanalEntry.Column c:list){
//            System.out.println("添加后的数据："+c.getName()+":"+c.getValue());
//        }
//    }
//    @UpdateListenPoint
//    public void updateListener(CanalEntry.EventType eventType, CanalEntry.RowData rowData){
//        List<CanalEntry.Column> beforeColumnsList = rowData.getBeforeColumnsList();
//        System.out.println("修改前的数据");
//        for(CanalEntry.Column c:beforeColumnsList){
//            System.out.println(c.getName()+":"+c.getValue());
//        }
//        List<CanalEntry.Column> afterColumnsList = rowData.getAfterColumnsList();
//        System.out.println("修改后的数据");
//        for(CanalEntry.Column c:afterColumnsList){
//            System.out.println(c.getName()+":"+c.getValue());
//        }
//    }
//    @DeleteListenPoint
//    public void deleteListener(CanalEntry.EventType eventType, CanalEntry.RowData rowData){
//        List<CanalEntry.Column> list = rowData.getBeforeColumnsList();
//        System.out.println("删除前的数据");
//        for(CanalEntry.Column c:list){
//            System.out.println(c.getName()+":"+c.getValue());
//        }
//    }

    /**
     * 只要数据进行了增删改操作，这边就能监听到数据，查询到对应的category_id，然后我们就可以根据这个id进行查询，并保存到redis中
     * @param eventType
     * @param rowData
     */
    @ListenPoint(eventType = {CanalEntry.EventType.DELETE, CanalEntry.EventType.INSERT, CanalEntry.EventType.UPDATE},table = {"tb_content","tb_content_category"},schema = {"changgou_content"},destination = "example")
    public void defindLisener(CanalEntry.EventType eventType, CanalEntry.RowData rowData){
        //获取id，然后根据id进行查询，然后保存到redis中
        String categoryId=getCategoryId(eventType, rowData);
        Result<List<Content>> result = contentFeign.findByCategory(Long.parseLong(categoryId));
        List<Content> contentList = result.getData();
        System.out.println(contentList);
        redisTemplate.boundValueOps("content_"+categoryId).set(JSON.toJSONString(contentList));
    }

    private String getCategoryId(CanalEntry.EventType eventType, CanalEntry.RowData rowData) {
        String categoryId="";
        if(eventType.equals(CanalEntry.EventType.DELETE)){
            List<CanalEntry.Column> beforeColumnsList = rowData.getBeforeColumnsList();
            for(CanalEntry.Column c:beforeColumnsList){
                if(c.getName().equalsIgnoreCase("category_id")){
                    categoryId=c.getValue();
                }
            }
        }else{
            List<CanalEntry.Column> afterColumnsList = rowData.getAfterColumnsList();
            for (CanalEntry.Column c:afterColumnsList){
                if(c.getName().equalsIgnoreCase("category_id")){
                    categoryId=c.getValue();
                }
            }
        }
        return categoryId;
    }

}
