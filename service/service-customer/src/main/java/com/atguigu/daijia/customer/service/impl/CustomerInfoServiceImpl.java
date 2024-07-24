package com.atguigu.daijia.customer.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.customer.config.WxLoginConfigOperator;
import com.atguigu.daijia.customer.mapper.CustomerInfoMapper;
import com.atguigu.daijia.customer.mapper.CustomerLoginLogMapper;
import com.atguigu.daijia.customer.service.CustomerInfoService;
import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.entity.customer.CustomerLoginLog;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerInfoServiceImpl extends ServiceImpl<CustomerInfoMapper, CustomerInfo> implements CustomerInfoService {
    @Autowired
    private WxLoginConfigOperator wxLoginConfigOperator;
    @Autowired
    private CustomerInfoMapper customerInfoMapper;
    @Autowired
    private CustomerLoginLogMapper customerLoginLogMapper;

    @Override
    public Long login(String code) {
        //1.发送请求到微信服务器，得到openid
        String openid = null;
        try {
            WxMaJscode2SessionResult sessionInfo = wxLoginConfigOperator.wxMaService().getUserService().getSessionInfo(code);
            openid =sessionInfo.getOpenid();
        } catch (WxErrorException e) {
            throw new RuntimeException(e);
        }
        //2.根据openid查询数据库是否是第一次登录
        LambdaQueryWrapper<CustomerInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CustomerInfo::getWxOpenId, openid);
        CustomerInfo customerInfo = customerInfoMapper.selectOne(queryWrapper);
        //3.如果是第一次登录，进行注册操作
        if(customerInfo==null){
             customerInfo = new CustomerInfo();
             customerInfo.setWxOpenId(openid);
             customerInfo.setAvatarUrl("https://big-event1800.oss-cn-beijing.aliyuncs.com/avatar.png");
            customerInfo.setNickname(String.valueOf(System.currentTimeMillis()));
            customerInfoMapper.insert(customerInfo);
        }
        //4.数据库中存在该用户，则记录用户登录日志
        CustomerLoginLog customerLoginLog = new CustomerLoginLog();
        customerLoginLog.setCustomerId(customerInfo.getId());
        customerLoginLog.setMsg("小程序登录");
        customerLoginLogMapper.insert(customerLoginLog);
        return customerInfo.getId();
    }

    @Override
    public CustomerLoginVo getCustomerLoginInfo(Long customerId) {
        //1.根据传递过来的用户id，查询数据库是否存在该用户
        LambdaQueryWrapper<CustomerInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CustomerInfo::getId,customerId);
        CustomerInfo customerInfo = customerInfoMapper.selectOne(queryWrapper);

        if(customerInfo==null){
            //3.不存在，就本应该跳转到注册页面，这里就直接先抛个异常
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        //2.若存在用户信息,返回用户信息
        CustomerLoginVo customerLoginVo = new CustomerLoginVo();
        BeanUtils.copyProperties(customerInfo,customerLoginVo);
        String phone = customerInfo.getPhone();

        customerLoginVo.setIsBindPhone(StringUtils.hasText(phone));

        return customerLoginVo;
    }
}
