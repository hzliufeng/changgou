package com.changgou.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.changgou.SearchApplication;
import com.changgou.goods.feign.SkuFeign;
import com.changgou.goods.pojo.Sku;
import com.changgou.search.dao.SkuEsMapper;
import com.changgou.search.pojo.SkuInfo;
import com.changgou.search.service.SkuInfoService;
import io.swagger.models.auth.In;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchResultMapper;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.aggregation.impl.AggregatedPageImpl;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class SkuInfoServiceImpl implements SkuInfoService {
    @Autowired
    private SkuEsMapper esMapper;
    @Autowired
    private SkuFeign skuFeign;
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    /**
     * 思路：
     * 1.创建一个document的实体类，对应要导入索引库的字段
     * 2.创建一个dao接口，继承ElasticSearchRepository
     * 3.创建service，注入mapper和feign。通过feign调用查询所有数据，然后调用mapper的方法导入索引库
     */
    @Override
    public void importData() {
        List<Sku> skuList = skuFeign.findAll().getData();
        //将所有的sku转换成skuinfo
        List<SkuInfo> skuInfoList = JSON.parseArray(JSON.toJSONString(skuList), SkuInfo.class);
        //动态导入规格数据
        for(SkuInfo skuInfo:skuInfoList){
            Map<String,Object> specMap = JSON.parseObject(skuInfo.getSpec());
            skuInfo.setSpecMap(specMap);
        }
        //添加到索引库
        esMapper.saveAll(skuInfoList);
    }

    /**
     * 多条件搜索
     * @param searchMap
     * @return
     */
    @Override
    public Map<String, Object> search(Map<String, String> searchMap) {
        NativeSearchQueryBuilder nativeSearchQueryBuilder = getBasicQueryBuilder(searchMap);

        HashMap<String, Object> resultMap = getKeywordsSearch(nativeSearchQueryBuilder);
        //如果用户没有点击过分类或品牌，则查询分类或品牌并封装到resultMap中显示，否则就不查询封装
        if(searchMap==null||StringUtils.isEmpty(searchMap.get("category"))){
            List<String> categoryList = getCategoryList(nativeSearchQueryBuilder);
            resultMap.put("categoryList",categoryList); //分类
        }
        if(searchMap==null||StringUtils.isEmpty(searchMap.get("brand"))){
            List<String> brandList = getbrandList(nativeSearchQueryBuilder);
            resultMap.put("brandList",brandList);
        }

        Map<String, Set<String>> allSpecMap = getSpecList(nativeSearchQueryBuilder);
        resultMap.put("specList",allSpecMap);
        return resultMap;
    }

    private Map<String, Set<String>> getSpecList(NativeSearchQueryBuilder nativeSearchQueryBuilder) {
        //先根据规格进行分组
        nativeSearchQueryBuilder.addAggregation(AggregationBuilders.terms("skuSpec").field("spec.keyword").size(10000));
        AggregatedPage<SkuInfo> specPage = elasticsearchTemplate.queryForPage(nativeSearchQueryBuilder.build(), SkuInfo.class);
        StringTerms stringTerms=specPage.getAggregations().get("skuSpec");
        List<String> specList=new ArrayList<>();
        for(StringTerms.Bucket bucket:stringTerms.getBuckets()){
            specList.add(bucket.getKeyAsString());
        }
        //再循环specList将每个spec转换成map<String,String>,并合并到Map<String,Set<String>>中
        Map<String, Set<String>> allSpecMap = getAllSpecMap(specList);
        return allSpecMap;
    }

    private Map<String, Set<String>> getAllSpecMap(List<String> specList) {
        Map<String, Set<String>> allSpecMap=new HashMap<>();
        for(String spec:specList){
            //将每个spec转换成map
            Map<String,String> specMap = JSON.parseObject(spec, Map.class);
            //循环map进行合并
            for(Map.Entry<String,String> entry:specMap.entrySet()){
                String key = entry.getKey();
                String value = entry.getValue();
                //通过key获取set集合
                Set<String> specSet = allSpecMap.get(key);
                if(specSet==null){
                    specSet=new HashSet<>();
                }
                specSet.add(value);
                allSpecMap.put(key,specSet);
            }

        }
        return allSpecMap;
    }

    private List<String> getbrandList(NativeSearchQueryBuilder nativeSearchQueryBuilder) {
        //添加品牌分组
        nativeSearchQueryBuilder.addAggregation(AggregationBuilders.terms("skuBrand").field("brandName").size(100));
        AggregatedPage<SkuInfo> brandPage = elasticsearchTemplate.queryForPage(nativeSearchQueryBuilder.build(), SkuInfo.class);
        //获取结果
        StringTerms stringTerms = brandPage.getAggregations().get("skuBrand");
        List<String> brandList=new ArrayList<>();
        for(StringTerms.Bucket bucket:stringTerms.getBuckets()){
            String keyAsString = bucket.getKeyAsString();
            brandList.add(keyAsString);
        }
        return brandList;
    }

    private NativeSearchQueryBuilder getBasicQueryBuilder(Map<String, String> searchMap) {
        //创建query对象的对象
        NativeSearchQueryBuilder nativeSearchQueryBuilder=new NativeSearchQueryBuilder();
        //创建组合查询对象
        BoolQueryBuilder boolQueryBuilder=QueryBuilders.boolQuery();
        //封装查询对象
        if(searchMap!=null&&searchMap.size()>0){
            String keywords=searchMap.get("keywords");
            //如果关键词不为空，则搜索关键词数据
            if(!StringUtils.isEmpty(keywords)){
                //nativeSearchQueryBuilder.withQuery(QueryBuilders.queryStringQuery(keywords).field("name"));
                boolQueryBuilder.must(QueryBuilders.queryStringQuery(keywords).field("name"));
            }
            //如果分类不为空
            if(!StringUtils.isEmpty(searchMap.get("category"))){
                boolQueryBuilder.must(QueryBuilders.termQuery("categoryName",searchMap.get("category")));
            }
            //如果品牌不为空
            if(!StringUtils.isEmpty(searchMap.get("brand"))){
                boolQueryBuilder.must(QueryBuilders.termQuery("brandName",searchMap.get("brand")));
            }

            /**
             * 规格过滤思路：
             *   遍历searchMap，根据key进行判断，如果以spec_开头的就是规格，然后根据这个规格进行过滤查询
             */
            for (Map.Entry<String, String> entry : searchMap.entrySet()) {
                String key = entry.getKey();
                if(key.startsWith("spec_")){
                    //说明是规格，然后根据规格进行过滤
                    boolQueryBuilder.must(QueryBuilders.termQuery("specMap."+key.substring(5)+".keyword",entry.getValue()));
                }
            }

            /**
             * 价格区间过滤思路：
             *   首先判断价格不为空，再对价格中的元和以上等字眼进行消除
             *   然后根据-进行切分
             *   如果数组长度大于0，则price>str[0]
             *   如果数组长度大于1，则price<str[1]
             */
            String price=searchMap.get("price");
            if(!StringUtils.isEmpty(price)){
                price=price.replace("元","").replace("以上","");
                String[] prices = price.split("-");
                if(prices!=null&&prices.length>0){
                    boolQueryBuilder.must(QueryBuilders.rangeQuery("price").gt(Integer.parseInt(prices[0])));
                    if(prices.length==2){
                        boolQueryBuilder.must(QueryBuilders.rangeQuery("price").lte(Integer.parseInt(prices[1])));
                    }
                }
            }

            /**
             * 排序思路：
             *   1.首先获取指定排序的域和指定的排序的规则
             *   2.判断指定的域和规则是否为空，如果不为空，则根据这些进行排序
             */
            String sortField = searchMap.get("sortField");  //指定排序的域
            String sortRule = searchMap.get("sortRule");   //指定排序规则，是升序还是降序
            if(!StringUtils.isEmpty(sortField)&&!StringUtils.isEmpty(sortRule)){
                nativeSearchQueryBuilder.withSort(new FieldSortBuilder(sortField).order(SortOrder.valueOf(sortRule)));
            }
            /**
             * 总结：
             *      termQuery不会对搜索的词进行分词
             *      queryStringQuery和matchQuery会对搜索的词进行分词
             */


        }

        /**
         * 分页思路：
         *   从参数中获取分页，前提是参数searMap不能为空，将获取到的页码值进行转换
         *   如果出现异常，就返回1
         *   否则就正常返回数值
         */
        Integer pageNum=getPageNum(searchMap);
        Integer size=20;  //默认查询的数据条数
        nativeSearchQueryBuilder.withPageable(PageRequest.of(pageNum-1,size));
        nativeSearchQueryBuilder.withQuery(boolQueryBuilder);
        return nativeSearchQueryBuilder;
    }
    private Integer getPageNum(Map<String,String> searchMap){
        if(searchMap!=null){
            String page = searchMap.get("page");
            try{
                return Integer.parseInt(page);
            }catch (Exception e){
                return 1;
            }
        }
        return 1;
    }
    private HashMap<String, Object> getKeywordsSearch(NativeSearchQueryBuilder nativeSearchQueryBuilder) {
        /**
         * 高亮显示:用在关键词查询上
         * 思路：
         *   1.首先在关键词搜索这地方添加高亮
         *     先创建一个创建query的对象NativeSearchQueryBuilder对象，然后用这个对象添加高亮操作withHighlightFields,
         *     参数指定哪个域进行高亮显示，并指定前后缀以及碎片长度
         *
         *   2.按照关键字进行搜索，调用esTemplate的queryForPage方法，其中第三个参数中创建匿名内部类，在内部类的方法中
         *      获取所有数据，进行遍历，获取非高亮数据并转化成SkuInfo对象
         *      获取高亮数据并把高亮数据读取出来getFragments（）方法，并拼接成一个带有高亮的字符串
         *      将非高亮数据替换成高亮数据
         *      返回
         */
        //指定name域进行高亮显示
        HighlightBuilder.Field field=new HighlightBuilder.Field("name");
        field.preTags("<em style='color:red'>");
        field.postTags("</em>");
        field.fragmentSize(200);
        nativeSearchQueryBuilder.withHighlightFields(field);
        //按关键字进行搜索
        AggregatedPage<SkuInfo> page = elasticsearchTemplate.queryForPage(nativeSearchQueryBuilder.build(), SkuInfo.class, new SearchResultMapper() {
            @Override
            public <T> AggregatedPage<T> mapResults(SearchResponse searchResponse, Class<T> aClass, Pageable pageable) {
                SearchHits hits = searchResponse.getHits();
                List<T> list=new ArrayList<>();
                for(SearchHit hit:hits){
                    SkuInfo skuInfo = JSON.parseObject(hit.getSourceAsString(), SkuInfo.class);
                    HighlightField highlightField = hit.getHighlightFields().get("name");
                    if(highlightField!=null&&highlightField.getFragments()!=null){
                        //把高亮数据读取出来
                        Text[] fragments = highlightField.getFragments();
                        StringBuffer stringBuffer=new StringBuffer();
                        for (Text fragment : fragments) {
                            stringBuffer.append(fragment.toString());
                        }
                        //非高亮数据中指定的域替换成高亮数据
                        skuInfo.setName(stringBuffer.toString());
                    }
                    list.add((T) skuInfo);
                }
                //将数据返回
                return new AggregatedPageImpl<>(list,pageable,searchResponse.getHits().getTotalHits());
            }
        });


        //分析数据
        HashMap<String,Object> resultMap=new HashMap<>();
        resultMap.put("total",page.getTotalElements());  //总记录数
        resultMap.put("totalPages",page.getTotalPages());  //总页数
        resultMap.put("rows",page.getContent())  ; //记录
        return resultMap;
    }

    private List<String> getCategoryList(NativeSearchQueryBuilder nativeSearchQueryBuilder) {
        //分组查询分类集合,添加分组
        nativeSearchQueryBuilder.addAggregation(AggregationBuilders.terms("skuCategory").field("categoryName").size(100));
        AggregatedPage<SkuInfo> categoryPage = elasticsearchTemplate.queryForPage(nativeSearchQueryBuilder.build(), SkuInfo.class);

        //获取数据
        StringTerms stringTerms=categoryPage.getAggregations().get("skuCategory");
        List<String> categoryList=new ArrayList<>();
        for(StringTerms.Bucket bucket:stringTerms.getBuckets()){
            String keyAsString = bucket.getKeyAsString();
            categoryList.add(keyAsString);
        }
        return categoryList;
    }


}
