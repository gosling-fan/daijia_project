package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.map.client.MapFeignClient;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.entity.order.OrderStatusLog;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.customer.SubmitOrderForm;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.vo.dispatch.NewOrderTaskVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.order.mapper.OrderInfoMapper;
import com.atguigu.daijia.order.mapper.OrderStatusLogMapper;
import com.atguigu.daijia.order.service.OrderInfoService;
import com.atguigu.daijia.rules.client.FeeRuleFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {
    @Autowired
    private  OrderInfoMapper orderInfoMapper;
    @Autowired
    private OrderStatusLogMapper orderStatusLogMapper;
    @Autowired
    private MapFeignClient mapFeignClient;
    @Autowired
    private FeeRuleFeignClient feeRuleFeignClient;
    @Autowired
    private OrderInfoFeignClient orderInfoFeignClient;
    @Autowired
    private NewOrderFeignClient newOrderFeignClient;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Override
    public Long saveOrderInfo(OrderInfoForm orderInfoForm) {
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(orderInfoForm, orderInfo);
        String orderNo = UUID.randomUUID().toString().replaceAll("-","");
        orderInfo.setStatus(OrderStatus.WAITING_ACCEPT.getStatus());
        orderInfo.setOrderNo(orderNo);
        orderInfoMapper.insert(orderInfo);

        //记录日志
        this.log(orderInfo.getId(), orderInfo.getStatus());

        //接单标识，标识不存在了说明不在等待接单状态了
        redisTemplate.opsForValue().set(RedisConstant.ORDER_ACCEPT_MARK+orderInfo.getId(), "0", RedisConstant.ORDER_ACCEPT_MARK_EXPIRES_TIME, TimeUnit.MINUTES);
        return orderInfo.getId();
    }

    @Override
    public Integer getOrderStatus(Long orderId) {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, orderId);
        queryWrapper.select(OrderInfo::getStatus);
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        if(null == orderInfo) {
            //返回null，feign解析会抛出异常，给默认值，后续会用
            return OrderStatus.NULL_ORDER.getStatus();
        }
        return orderInfo.getStatus();
    }

    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {
        //抢单成功或取消订单，都会删除该key，redis判断，减少数据库压力
        if(!redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK+orderId)) {
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        // 初始化分布式锁，创建一个RLock实例
        RLock lock = redissonClient.getLock(RedisConstant.ROB_NEW_ORDER_LOCK + orderId);
        try {
            /**
             * TryLock是一种非阻塞式的分布式锁，实现原理：Redis的SETNX命令
             * 参数：
             *     waitTime：等待获取锁的时间
             *     leaseTime：加锁的时间
             */
            boolean flag = lock.tryLock(RedisConstant.ROB_NEW_ORDER_LOCK_WAIT_TIME,RedisConstant.ROB_NEW_ORDER_LOCK_LEASE_TIME, TimeUnit.SECONDS);
            //获取到锁
            if (flag){
                //二次判断，防止重复抢单
                if(!redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK)) {
                    //抢单失败
                    throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
                }

                //修改订单状态
                //update order_info set status = 2, driver_id = #{driverId} where id = #{id}
                //修改字段
                OrderInfo orderInfo = new OrderInfo();
                orderInfo.setId(orderId);
                orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
                orderInfo.setAcceptTime(new Date());
                orderInfo.setDriverId(driverId);
                int rows = orderInfoMapper.updateById(orderInfo);
                if(rows != 1) {
                    //抢单失败
                    throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
                }

                //记录日志
                this.log(orderId, orderInfo.getStatus());

                //删除redis订单标识
                redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK);
            }
        } catch (InterruptedException e) {
            //抢单失败
            throw new GuiguException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        } finally {
            if(lock.isLocked()) {
                lock.unlock();
            }
        }
        return true;
    }


    public void log(Long orderId, Integer status) {
        OrderStatusLog orderStatusLog = new OrderStatusLog();
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(status);
        orderStatusLog.setOperateTime(new Date());
        orderStatusLogMapper.insert(orderStatusLog);
    }
}
