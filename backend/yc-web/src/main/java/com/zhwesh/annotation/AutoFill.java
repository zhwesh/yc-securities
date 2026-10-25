package com.zhwesh.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 自动填充时间（使用springAOP实现）
// 用法：在Mapper的方法上添加此注解
// 便可将其第一个参数的createTime字段填充
// 前提：第一个参数必须有上述四个字段的setter
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {
}
