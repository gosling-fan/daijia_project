package com.atguigu.daijia.map.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.map.properties.MapProperties;
import com.atguigu.daijia.map.service.MapService;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class MapServiceImpl implements MapService {
    @Autowired
    private RestTemplate restTemplate;

//    @Value( "${tencent.map.key}")
//    private String key;    // 腾讯地图服务
    @Autowired
    private MapProperties mapProperties;
    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        //1.首先定义一个请求url
        String url = "https://apis.map.qq.com/ws/direction/v1/driving/?from={from}&to={to}&key={key}";
        //2.设置url中的参数
        HashMap<String, String> map = new HashMap<>();
        String startPoint =calculateDrivingLineForm.getStartPointLatitude()+","+calculateDrivingLineForm.getStartPointLongitude();
        String endPoint = calculateDrivingLineForm.getEndPointLatitude()+","+calculateDrivingLineForm.getEndPointLongitude();
        map.put("from",startPoint);
        map.put("to",endPoint);
        map.put("key",mapProperties.getKey());
        JSONObject result = restTemplate.getForObject(url, JSONObject.class, map);
//
        int status = result.getIntValue("status");
        if(status!=0){
            throw  new GuiguException(ResultCodeEnum.MAP_FAIL);
        }
        JSONObject route = result.getJSONObject("result").getJSONArray("routes").getJSONObject(0);
        DrivingLineVo drivingLineVo = new DrivingLineVo();
        drivingLineVo.setDistance(route.getBigDecimal("duration"));
        BigDecimal distance = route.getBigDecimal("distance").divide(new BigDecimal(1000)).setScale(2, RoundingMode.HALF_UP);
        drivingLineVo.setDistance(distance);
        drivingLineVo.setPolyline(route.getJSONArray("polyline"));
        return drivingLineVo;
    }
}
