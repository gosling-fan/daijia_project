package com.atguigu.daijia.customer.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.customer.client.CustomerInfoFeignClient;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerServiceImpl implements CustomerService {
    @Autowired
    private CustomerInfoFeignClient customerInfoFeignClient;
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public String login(String code) {
        //1.远程调用用户登录服务接口，返回用户id
        Result<Long> result = customerInfoFeignClient.login(code);
        Integer resultCode = result.getCode();
        //2.判断用户id是否为空，为空则抛出异常 登录失败
        //如果状态码不为200,
        if(resultCode!=200){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        //获取用户id
        Long id = result.getData();
        //3.不为空，根据返回id，生产token字符串
        String token = UUID.randomUUID().toString().replace("-","");

        //4.将token保存到redis中
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX+token,id.toString(),RedisConstant.USER_LOGIN_KEY_TIMEOUT,
                TimeUnit.SECONDS);
        return token;
    }

    @Override
    public CustomerLoginVo getCustomerLoginInfo(String token) {
        //1.从前端拿到token，与前缀进行组合，
        String newToken = RedisConstant.USER_LOGIN_KEY_PREFIX+token;
        //2.利用token，从redis中查询出用户id
         String id= (String) redisTemplate.opsForValue().get(newToken);
        //3.若redis不能查到用户id，抛出异常，用户登录信息可能失效，或者token不正确
        if(id==null){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        //4.能查到用户id，远程调用用户服务，查询用户信息
        Result<CustomerLoginVo> result = customerInfoFeignClient.getCustomerLoginInfo(Long.parseLong(id));
        Integer code = result.getCode();
        if(code!=200){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        CustomerLoginVo customerLoginVo = result.getData();
        if(customerLoginVo==null){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        //5.返回用户信息
        return  customerLoginVo;
    }

    @Override
    public CustomerLoginVo getCustomerInfo(Long customerId) {
        //3.若redis不能查到用户id，抛出异常，用户登录信息可能失效，或者token不正确
        if(customerId==null){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        //4.能查到用户id，远程调用用户服务，查询用户信息
        Result<CustomerLoginVo> result = customerInfoFeignClient.getCustomerLoginInfo(customerId);
        Integer code = result.getCode();
        if(code!=200){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        CustomerLoginVo customerLoginVo = result.getData();
        if(customerLoginVo==null){
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        //5.返回用户信息
        return  customerLoginVo;
    }
}
