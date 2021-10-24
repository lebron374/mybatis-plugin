package com.tuacy.mybatis.interceptor.interceptor.param;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.util.Properties;

@Intercepts({
        @Signature(type = ParameterHandler.class, method = "setParameters", args = PreparedStatement.class)
})
public class ParamInterceptor implements Interceptor {


    private static final ObjectFactory DEFAULT_OBJECT_FACTORY = new DefaultObjectFactory();
    private static final ObjectWrapperFactory DEFAULT_OBJECT_WRAPPER_FACTORY = new DefaultObjectWrapperFactory();
    private static final ReflectorFactory REFLECTOR_FACTORY = new DefaultReflectorFactory();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        // 获取拦截器拦截的设置参数对象DefaultParameterHandler
        ParameterHandler parameterHandler = (ParameterHandler) invocation.getTarget();

        // 通过mybatis的反射来获取对应的值
        MetaObject metaParameterHandler = MetaObject.forObject(parameterHandler, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY, REFLECTOR_FACTORY);
        MappedStatement mappedStatement = (MappedStatement) metaParameterHandler.getValue("mappedStatement");
        Object parameterObject = metaParameterHandler.getValue("parameterObject");

        // id字段对应执行的SQL的方法的全路径，包含类名和方法名
        String id = mappedStatement.getId();
        String className = id.substring(0, id.lastIndexOf("."));
        String methodName = id.substring(id.lastIndexOf(".") + 1);

        // 动态加载类并获取类中的方法
        final Method[] methods = Class.forName(className).getMethods();

        // 遍历类的所有方法并找到此次调用的方法
        for (Method method : methods) {
            if (method.getName().equalsIgnoreCase(methodName)
                    && method.isAnnotationPresent(ParamAnnotation.class)) {

                // 获取方法上的注解以及注解对应的参数
                ParamAnnotation paramAnnotation = method.getAnnotation(ParamAnnotation.class);
                String srcKey = paramAnnotation.srcKey()[0];
                String destKey = paramAnnotation.destKey()[0];

                // 反射获取参数对象
                MetaObject param = MetaObject.forObject(parameterObject, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY, REFLECTOR_FACTORY);
                Object srcValue = param.getValue(srcKey);

                // 动态加工指定参数
                String destValue = String.valueOf(srcValue) + "fix";

                // 将修改后的动态参数添加到请求参数当中
                param.setValue(destKey, destValue);

                param.setValue(destKey, null);
                break;
            }
        }

        BoundSql boundSql = mappedStatement.getBoundSql(parameterObject);
        String newSql = boundSql.getSql();

        BoundSql newBoundSql = new BoundSql(mappedStatement.getConfiguration(), newSql,
                boundSql.getParameterMappings(), boundSql.getParameterObject());

        MappedStatement newMappedStatement = newMappedStatement(mappedStatement, new BoundSqlSqlSource(newBoundSql));
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            String prop = mapping.getProperty();
            if (boundSql.hasAdditionalParameter(prop)) {
                newBoundSql.setAdditionalParameter(prop, boundSql.getAdditionalParameter(prop));
            }
        }

        metaParameterHandler.setValue("mappedStatement", newMappedStatement);
        // 回写parameterObject对象
        metaParameterHandler.setValue("parameterObject", parameterObject);
        return invocation.proceed();
    }


    class BoundSqlSqlSource implements SqlSource {
        private BoundSql boundSql;
        public BoundSqlSqlSource(BoundSql boundSql) {
            this.boundSql = boundSql;
        }

        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            return boundSql;
        }

    }

    private MappedStatement newMappedStatement (MappedStatement ms, SqlSource newSqlSource) {
        MappedStatement.Builder builder = new
                MappedStatement.Builder(ms.getConfiguration(), ms.getId(), newSqlSource, ms.getSqlCommandType());
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());

        if (ms.getKeyProperties() != null && ms.getKeyProperties().length != 0) {
            StringBuilder keyProperties = new StringBuilder();
            for (String keyProperty : ms.getKeyProperties()) {
                keyProperties.append(keyProperty).append(",");
            }
            keyProperties.delete(keyProperties.length() - 1, keyProperties.length());
            builder.keyProperty(keyProperties.toString());
        }

        builder.timeout(ms.getTimeout());
        builder.parameterMap(ms.getParameterMap());
        builder.resultMaps(ms.getResultMaps());
        builder.resultSetType(ms.getResultSetType());
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());
        return builder.build();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {

    }
}
