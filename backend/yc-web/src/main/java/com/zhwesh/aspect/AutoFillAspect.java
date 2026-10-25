package com.zhwesh.aspect;

import com.zhwesh.annotation.AutoFill;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 实现注解AutoFill的功能
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    @Pointcut("@annotation(com.zhwesh.annotation.AutoFill)")
    public void pointcut() { }

    @Around("pointcut()")
    public Object autoFill(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        log.info("自动填充更改/创建时间、更改/创建者");
        // 获取原始目标方法上的注解
        MethodSignature methodSignature = (MethodSignature) proceedingJoinPoint.getSignature();
        AutoFill autoFill = methodSignature.getMethod().getAnnotation(AutoFill.class);
        // 获取原始目标方法的参数
        Object[] args = proceedingJoinPoint.getArgs();
        if (args == null || args.length == 0) {
            return proceedingJoinPoint.proceed();
        }

        // 获取要操作的对象（原始目标方法的第一个参数）
        Object obj = args[0];

        obj.getClass()
            .getMethod("setCreateTime", LocalDateTime.class)
            .invoke(obj, LocalDateTime.now());

        return proceedingJoinPoint.proceed();
    }
}