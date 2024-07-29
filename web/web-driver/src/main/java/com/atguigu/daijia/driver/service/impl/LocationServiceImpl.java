package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.LocationService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.form.map.UpdateOrderLocationForm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class LocationServiceImpl implements LocationService {
    @Autowired
    private LocationFeignClient locationFeignClient;
    @Autowired
    private DriverInfoFeignClient driverInfoFeignClient;
    @Override
    public Boolean updateDriverLocation(UpdateDriverLocationForm updateDriverLocationForm) {
        //1.首先查询司机个性化设置状态是否是 开始接单状态，
        Result<DriverSet> driverSet = driverInfoFeignClient.getDriverSet(updateDriverLocationForm.getDriverId());
        //2.是,则可以跟新司机位置信息。
        if(driverSet.getData().getServiceStatus()==1)
        return locationFeignClient.updateDriverLocation(updateDriverLocationForm).getData();
        else
            throw new GuiguException(ResultCodeEnum.NO_START_SERVICE);
    }

    @Override
    public Boolean updateOrderLocationToCache(UpdateOrderLocationForm updateOrderLocationForm) {
        return locationFeignClient.updateOrderLocationToCache(updateOrderLocationForm).getData();
    }
}
