package cn.myiml.theims.core.rule.load;

import cn.myiml.theims.core.enums.PatternEnum;
import cn.myiml.theims.core.enums.ProcessTypeEnum;
import cn.myiml.theims.core.model.RuleConfigModel;
import cn.myiml.theims.core.model.VerifyRulesConfigModel;
import cn.myiml.theims.core.verify.annotation.VerifyField;
import cn.myiml.theims.core.verify.annotation.VerifyFields;
import com.google.common.base.Splitter;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ͨ��ע�ⷽʽ����У�����
 * @author yangzhou
 */
public class AnnotationLoadRule implements LoadVerifyRule<VerifyRulesConfigModel>{

    /**
     * ���ݷ���ȫ�޶�����ȡУ�����
     * @param routeName routeName ����ȫ�޶��� ������ ��ȫ�޶��� + "&" + ������
     * @return ConcurrentHashMap<String,List<VerifyRulesConfigModel>> keyΪ����ȫ�޶���
     * @since 1.0.0
     */
    @SneakyThrows
    @Override
    public ConcurrentHashMap<String,List<VerifyRulesConfigModel>> loadRule(String routeName) {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        Iterable<String> routes = Splitter.on('&').trimResults().omitEmptyStrings().split(routeName);
        List<String> routeNames = new ArrayList<>();
        routes.forEach(routeNames::add);
        int routeSplitSize = 2;
        if (routeNames.size() == routeSplitSize){
            String clazzRoute = routeNames.get(0);
            String methodName = routeNames.get(1);
            try {
                Class<? extends Object> clazz = classLoader.loadClass(clazzRoute);
                Method method = null;
                Method[] methods = clazz.getDeclaredMethods();
                for (int i = 0; i < methods.length; i++) {
                    if (methodName.equals(methods[i].getName())){
                        method = methods[i];
                    }
                }
                return this.loadRuleForObject(method);
            } catch (ClassNotFoundException e) {
                throw new ClassNotFoundException("class:" + clazzRoute + " not fund");
            }
        }
        return null;
    }


    @Override
    public ConcurrentHashMap<String, List<VerifyRulesConfigModel>> loadRule(String routeName, Object object) {
        ProceedingJoinPoint joinPoint = (ProceedingJoinPoint) object;
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();;
        Object target = joinPoint.getTarget();
        try {
            Method currentMethod = target.getClass().getMethod(signature.getName(), signature.getParameterTypes());
            return this.loadRuleForObject(currentMethod);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ConcurrentHashMap<String, List<VerifyRulesConfigModel>> loadAllRules() {
        return new ConcurrentHashMap<>(0);
    }

    @Override
    public List<VerifyRulesConfigModel> loadAllRuleList() {
        return new ArrayList<>();
    }

    /**
     * ���ݷ���ʵ����ȡУ�����
     * @param loadObj ��ȡУ�����ķ���ʵ��
     * @return ConcurrentHashMap �����У����� keyΪ����ȫ�޶���, valueΪ����У�����
     * @since 1.0.0
     */
    @Override
    public ConcurrentHashMap<String,List<VerifyRulesConfigModel>> loadRuleForObject(Object loadObj) {
        ConcurrentHashMap<String,List<VerifyRulesConfigModel>> configMap = new ConcurrentHashMap<>(8);
        if (loadObj instanceof Method){
            Method method = (Method) loadObj;
            Annotation[] annotations = method.getAnnotations();
            List<RuleConfigModel> configModel = loadRulesByAnnotationArrays(annotations);
            String routeName = method.getDeclaringClass().getName() + "&" + method.getName();
            List<VerifyRulesConfigModel> configs = createRulesConfigModel(configModel, routeName);
            configMap.put(routeName, configs);
        }
        return configMap;
    }

    /**
     * ��������У����򼯺�
     * @param configModel ����У����� ��loadRulesByAnnotationArrays����
     * @param routeName ����·��ȫ�޶���
     * @return VerifyRulesConfigModel
     * @since 1.0.0
     */
    private List<VerifyRulesConfigModel> createRulesConfigModel(List<RuleConfigModel> configModel, String routeName) {
        List<VerifyRulesConfigModel> configs = new ArrayList<>();
        VerifyRulesConfigModel rulesConfigModel = new VerifyRulesConfigModel();
        rulesConfigModel.setRules(configModel);
        rulesConfigModel.setName(routeName);
        rulesConfigModel.setRoute(routeName);
        rulesConfigModel.setProcessType(ProcessTypeEnum.PROCESS_TYPE.name());
        rulesConfigModel.setTypeVal(PatternEnum.DEFAULT.name());
        configs.add(rulesConfigModel);
        return configs;
    }

    /**
     * ����������򼯺� ����ע������
     * @param annotations  {@link VerifyFields,VerifyField}
     * @return RuleConfigModel arrays
     * @since 1.0.0
     */
    private List<RuleConfigModel> loadRulesByAnnotationArrays(Annotation[] annotations){
        List<VerifyField> verifyFieldList = new ArrayList<>();
        for (Annotation annotation : annotations) {
            if (annotation instanceof VerifyFields) {
                VerifyFields verifyFields = (VerifyFields) annotation;
                VerifyField[] verifyFieldArrays = verifyFields.fields();
                verifyFieldList.addAll(Arrays.stream(verifyFieldArrays).collect(Collectors.toList()));
            }
            if (annotation instanceof VerifyField){
                verifyFieldList.add((VerifyField) annotation);
            }
        }

        return createRulesByAnnotation(verifyFieldList);
    }

    /**
     * ����У����� ���ݴ���ע�� {@link VerifyField}
     * @param verifyFieldList Annotation VerifyField Arrays
     * @return RuleConfigModel
     * @since 1.0.0
     */
    private List<RuleConfigModel> createRulesByAnnotation(List<VerifyField> verifyFieldList){
        List<RuleConfigModel> rules = new ArrayList<>();
        verifyFieldList.forEach(verifyField -> {
            RuleConfigModel ruleConfigModel = new RuleConfigModel();
            List<String[]> totalParams = new ArrayList<>();
            for (String param: verifyField.names()) {
                Iterable<String> lastSplits = Splitter.on('.').trimResults().omitEmptyStrings().split(param);
                List<String> params = new ArrayList<>();
                lastSplits.forEach(params::add);
                totalParams.add(params.toArray(new String[0]));
            }
            ruleConfigModel.setParamArrays(totalParams);
            try {
                ruleConfigModel.setMessage(new String(verifyField.message().getBytes("GBK")));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            ruleConfigModel.setCheckRule(verifyField.rule());
            String paramNames = Arrays.stream(verifyField.names()).reduce((s, s2) -> s + "," + s2).orElse("");
            ruleConfigModel.setParamName(paramNames);
            ruleConfigModel.setPattern(verifyField.pattern().name());
            rules.add(ruleConfigModel);
        });
        return rules;
    }
}
