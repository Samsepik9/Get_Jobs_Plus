import lombok.extern.slf4j.Slf4j;

// 并发相关导入不再需要
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class StartAll {

    // 定义支持的招聘平台名称
    private static final String PLATFORM_BOSS = "boss";
    private static final String PLATFORM_LIEPIN = "liepin";
    private static final String PLATFORM_JOB51 = "job51";
    private static final String PLATFORM_LAGOU = "lagou";
    private static final String PLATFORM_ZHILIAN = "zhilian";
    private static final String ALL_PLATFORMS = "all";

    public static void main(String[] args) {
        // 解析命令行参数，确定要运行的平台
        Set<String> platformsToRun = parsePlatformArgs(args);
        log.info("将执行的招聘平台: {}", platformsToRun);

        // 顺序执行各平台，避免并发覆盖全局驱动
        try {
            if (platformsToRun.contains(PLATFORM_JOB51)) { log.info("执行 Job51..."); executeTask("job51.Job51"); }
            if (platformsToRun.contains(PLATFORM_LAGOU)) { log.info("执行 Lagou..."); executeTask("lagou.Lagou"); }
            if (platformsToRun.contains(PLATFORM_ZHILIAN)) { log.info("执行 Zhilian..."); executeTask("zhilian.ZhiLian"); }
            if (platformsToRun.contains(PLATFORM_LIEPIN)) { log.info("执行 Liepin..."); executeTask("liepin.Liepin"); }
            if (platformsToRun.contains(PLATFORM_BOSS)) { log.info("执行 Boss..."); executeTask("boss.Boss"); }
        } catch (Exception e) {
            log.error("顺序执行任务时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 解析命令行参数，确定要运行的招聘平台
     * @param args 命令行参数
     * @return 要运行的平台集合
     */
    private static Set<String> parsePlatformArgs(String[] args) {
        Set<String> platforms = new HashSet<>();
        
        // 如果没有参数或者包含"all"，则运行所有平台
        if (args.length == 0 || containsAll(args)) {
            platforms.add(PLATFORM_BOSS);
            platforms.add(PLATFORM_LIEPIN);
            platforms.add(PLATFORM_JOB51);
            platforms.add(PLATFORM_LAGOU);
            platforms.add(PLATFORM_ZHILIAN);
            return platforms;
        }
        
        // 根据参数添加相应的平台
        for (String arg : args) {
            String lowerArg = arg.toLowerCase();
            if (PLATFORM_BOSS.equals(lowerArg)) {
                platforms.add(PLATFORM_BOSS);
            } else if (PLATFORM_LIEPIN.equals(lowerArg)) {
                platforms.add(PLATFORM_LIEPIN);
            } else if (PLATFORM_JOB51.equals(lowerArg)) {
                platforms.add(PLATFORM_JOB51);
            } else if (PLATFORM_LAGOU.equals(lowerArg)) {
                platforms.add(PLATFORM_LAGOU);
            } else if (PLATFORM_ZHILIAN.equals(lowerArg)) {
                platforms.add(PLATFORM_ZHILIAN);
            } else {
                log.warn("未知的平台参数: {}", arg);
            }
        }
        
        // 如果没有有效的平台参数，默认运行所有平台
        if (platforms.isEmpty()) {
            log.warn("没有提供有效的平台参数，将运行所有平台");
            platforms.add(PLATFORM_BOSS);
            platforms.add(PLATFORM_LIEPIN);
            platforms.add(PLATFORM_JOB51);
            platforms.add(PLATFORM_LAGOU);
            platforms.add(PLATFORM_ZHILIAN);
        }
        
        return platforms;
    }
    
    /**
     * 检查参数数组是否包含"all"关键字
     * @param args 参数数组
     * @return 是否包含"all"
     */
    private static boolean containsAll(String[] args) {
        for (String arg : args) {
            if (ALL_PLATFORMS.equals(arg.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 使用反射直接调用指定类的main方法
     *
     * @param className 要执行的类名
     * @throws Exception 如果发生错误
     */
    private static void executeTask(String className) throws Exception {
        try {
            log.info("尝试加载并执行类: {}", className);
            // 加载类
            Class<?> clazz = Class.forName(className);
            // 获取main方法
            java.lang.reflect.Method mainMethod = clazz.getMethod("main", String[].class);
            // 调用main方法
            mainMethod.invoke(null, (Object) new String[0]);
            log.info("类 {} 执行成功", className);
        } catch (ClassNotFoundException e) {
            String errorMsg = "找不到类: " + className;
            log.error(errorMsg);
            throw new Exception(errorMsg, e);
        } catch (NoSuchMethodException e) {
            String errorMsg = "类 " + className + " 没有找到main方法";
            log.error(errorMsg);
            throw new Exception(errorMsg, e);
        } catch (Exception e) {
            // 获取根本原因异常
            Throwable cause = e.getCause();
            String errorMsg = "执行类 " + className + " 时发生错误";
            if (cause != null && cause.getMessage() != null) {
                errorMsg += ": " + cause.getMessage();
                log.error(errorMsg, cause);
                throw new Exception(errorMsg, cause);
            } else if (e.getMessage() != null) {
                errorMsg += ": " + e.getMessage();
                log.error(errorMsg, e);
                throw new Exception(errorMsg, e);
            } else {
                log.error(errorMsg, e);
                throw new Exception(errorMsg + " (具体错误信息未知)", e);
            }
        }
    }
}
