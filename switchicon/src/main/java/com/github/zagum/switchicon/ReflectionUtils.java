package com.github.zagum.switchicon;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionUtils {
  public static boolean setValue(Object object, String fieldName, Object fieldValue) {
    Class<?> clazz = object.getClass();
    while (clazz != null) {
      try {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(object, fieldValue);
        return true;
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    return false;
  }

  public static boolean callMethod(Object object, String methodName, Class<?>... params) {
    Class<?> clazz = object.getClass();
    while (clazz != null) {
      try {
        Method method = clazz.getDeclaredMethod(methodName, params);
        method.setAccessible(true);
        method.invoke(object, null);
        return true;
      } catch (NoSuchMethodException e) {
        clazz = clazz.getSuperclass();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    return false;
  }
}
