package utils;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SelectOption;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

/**
 * Playwright工具类，提供浏览器自动化相关的功能
 */
public class PlaywrightUtil {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightUtil.class);

    /**
     * 设备类型枚举
     */
    public enum DeviceType {
        DESKTOP, // 桌面设备
        MOBILE // 移动设备
    }

    // 默认设备类型
    private static DeviceType defaultDeviceType = DeviceType.DESKTOP;

    // 默认平台名称（用于兼容性）
    private static final String DEFAULT_PLATFORM = "default";
    
    // 存储每个平台的Playwright资源
    private static class PlatformResources {
        Playwright playwright;
        Browser browser;
        BrowserContext desktopContext;
        BrowserContext mobileContext;
        Page desktopPage;
        Page mobilePage;
    }
    
    // 多平台资源映射
    private static final Map<String, PlatformResources> platformResourcesMap = new ConcurrentHashMap<>();
    
    // 默认超时时间（毫秒）
    private static final int DEFAULT_TIMEOUT = 30000;

    // 默认等待时间（毫秒）
    private static final int DEFAULT_WAIT_TIME = 10000;

    /**
     * 初始化Playwright及浏览器实例
     */
    public static void init() {
        init(DEFAULT_PLATFORM);
    }
    
    /**
     * 为特定平台初始化Playwright及浏览器实例
     * @param platformName 平台名称（如：boss, liepin, job51, lagou, zhilian）
     */
    public static void init(String platformName) {
        log.info("初始化 [{}] 平台的浏览器实例...", platformName);
        
        // 如果平台资源已存在，则先关闭
        if (platformResourcesMap.containsKey(platformName)) {
            closePlatform(platformName);
        }
        
        PlatformResources resources = new PlatformResources();
        
        // 启动Playwright
        resources.playwright = Playwright.create();

        // 创建浏览器实例
        resources.browser = resources.playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false) // 非无头模式，可视化调试
                .setSlowMo(50)); // 放慢操作速度，便于调试

        // 创建桌面浏览器上下文
        resources.desktopContext = resources.browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setUserAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"));

        // 创建移动设备浏览器上下文
        resources.mobileContext = resources.browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(375, 812)
                .setDeviceScaleFactor(3.0)
                .setIsMobile(true)
                .setHasTouch(true)
                .setUserAgent(
                        "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1"));

        // 创建桌面页面
        resources.desktopPage = resources.desktopContext.newPage();
        resources.desktopPage.setDefaultTimeout(DEFAULT_TIMEOUT);
        
        // 创建移动页面
        resources.mobilePage = resources.mobileContext.newPage();
        resources.mobilePage.setDefaultTimeout(DEFAULT_TIMEOUT);

        // 保存到映射中
        platformResourcesMap.put(platformName, resources);
        log.info("[{}] 平台浏览器实例初始化完成", platformName);
    }

    /**
     * 设置默认设备类型
     *
     * @param deviceType 设备类型
     */
    public static void setDefaultDeviceType(DeviceType deviceType) {
        defaultDeviceType = deviceType;
        log.info("已设置默认设备类型为: {}", deviceType);
    }

    /**
     * 获取特定平台和设备类型的页面
     *
     * @param platformName 平台名称
     * @param deviceType 设备类型
     * @return 对应的Page对象，如果不存在则返回null
     */
    private static Page getPage(String platformName, DeviceType deviceType) {
        PlatformResources resources = platformResourcesMap.get(platformName);
        if (resources == null) {
            log.error("未找到 [{}] 平台的浏览器资源，请先调用init方法初始化", platformName);
            return null;
        }
        return deviceType == DeviceType.DESKTOP ? resources.desktopPage : resources.mobilePage;
    }

    /**
     * 获取特定平台和设备类型的上下文
     *
     * @param platformName 平台名称
     * @param deviceType 设备类型
     * @return 对应的BrowserContext对象，如果不存在则返回null
     */
    private static BrowserContext getContext(String platformName, DeviceType deviceType) {
        PlatformResources resources = platformResourcesMap.get(platformName);
        if (resources == null) {
            log.error("未找到 [{}] 平台的浏览器资源，请先调用init方法初始化", platformName);
            return null;
        }
        return deviceType == DeviceType.DESKTOP ? resources.desktopContext : resources.mobileContext;
    }
    
    /**
     * 获取默认平台和指定设备类型的页面（兼容旧代码）
     */
    private static Page getPage(DeviceType deviceType) {
        return getPage(DEFAULT_PLATFORM, deviceType);
    }
    
    /**
     * 获取默认平台和指定设备类型的上下文（兼容旧代码）
     */
    private static BrowserContext getContext(DeviceType deviceType) {
        return getContext(DEFAULT_PLATFORM, deviceType);
    }

    /**
     * 关闭所有平台的Playwright及浏览器实例
     */
    public static void close() {
        for (String platformName : new ArrayList<>(platformResourcesMap.keySet())) {
            closePlatform(platformName);
        }
        log.info("所有平台的Playwright及浏览器实例已关闭");
    }
    
    /**
     * 关闭特定平台的Playwright及浏览器实例
     * @param platformName 平台名称
     */
    public static void closePlatform(String platformName) {
        PlatformResources resources = platformResourcesMap.get(platformName);
        if (resources != null) {
            if (resources.desktopPage != null) try { resources.desktopPage.close(); } catch (Exception e) {}
            if (resources.mobilePage != null) try { resources.mobilePage.close(); } catch (Exception e) {}
            if (resources.desktopContext != null) try { resources.desktopContext.close(); } catch (Exception e) {}
            if (resources.mobileContext != null) try { resources.mobileContext.close(); } catch (Exception e) {}
            if (resources.browser != null) try { resources.browser.close(); } catch (Exception e) {}
            if (resources.playwright != null) try { resources.playwright.close(); } catch (Exception e) {}
            
            platformResourcesMap.remove(platformName);
            log.info("[{}] 平台的Playwright及浏览器实例已关闭", platformName);
        }
    }

    /**
     * 导航到指定URL
     *
     * @param url        目标URL
     * @param deviceType 设备类型
     */
    public static void navigate(String url, DeviceType deviceType) {
        navigate(DEFAULT_PLATFORM, url, deviceType);
    }
    
    /**
     * 为特定平台导航到指定URL
     *
     * @param platformName 平台名称
     * @param url          目标URL
     * @param deviceType   设备类型
     */
    public static void navigate(String platformName, String url, DeviceType deviceType) {
        Page page = getPage(platformName, deviceType);
        if (page != null) {
            page.navigate(url);
            log.info("已导航到URL: {} (平台: {}, 设备类型: {})", url, platformName, deviceType);
        }
    }

    /**
     * 使用默认设备类型导航到指定URL
     *
     * @param url 目标URL
     */
    public static void navigate(String url) {
        navigate(DEFAULT_PLATFORM, url, defaultDeviceType);
    }
    
    /**
     * 为特定平台使用默认设备类型导航到指定URL
     *
     * @param platformName 平台名称
     * @param url          目标URL
     */
    public static void navigate(String platformName, String url) {
        navigate(platformName, url, defaultDeviceType);
    }

    /**
     * 移动设备导航到指定URL (兼容旧代码)
     *
     * @param url 目标URL
     */
    public static void mobileNavigate(String url) {
        navigate(DEFAULT_PLATFORM, url, DeviceType.MOBILE);
    }
    
    /**
     * 为特定平台的移动设备导航到指定URL
     *
     * @param platformName 平台名称
     * @param url          目标URL
     */
    public static void mobileNavigate(String platformName, String url) {
        navigate(platformName, url, DeviceType.MOBILE);
    }

    /**
     * 等待指定时间（秒）
     *
     * @param seconds 等待的秒数
     */
    public static void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sleep被中断", e);
        }
    }

    /**
     * 等待指定时间（毫秒）
     *
     * @param millis 等待的毫秒数
     */
    public static void sleepMillis(int millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sleep被中断", e);
        }
    }

    /**
     * 兼容SeleniumUtil的sleepByMilliSeconds方法
     *
     * @param milliSeconds 等待的毫秒数
     */
    public static void sleepByMilliSeconds(int milliSeconds) {
        sleepMillis(milliSeconds);
    }

    /**
     * 查找元素
     *
     * @param selector   元素选择器
     * @param deviceType 设备类型
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator findElement(String selector, DeviceType deviceType) {
        return findElement(DEFAULT_PLATFORM, selector, deviceType);
    }
    
    /**
     * 为特定平台查找元素
     *
     * @param platformName 平台名称
     * @param selector     元素选择器
     * @param deviceType   设备类型
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator findElement(String platformName, String selector, DeviceType deviceType) {
        Page page = getPage(platformName, deviceType);
        return page != null ? page.locator(selector) : null;
    }

    /**
     * 使用默认设备类型查找元素
     *
     * @param selector 元素选择器
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator findElement(String selector) {
        return findElement(DEFAULT_PLATFORM, selector, defaultDeviceType);
    }
    
    /**
     * 为特定平台使用默认设备类型查找元素
     *
     * @param platformName 平台名称
     * @param selector     元素选择器
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator findElement(String platformName, String selector) {
        return findElement(platformName, selector, defaultDeviceType);
    }

    /**
     * 查找元素并等待直到可见
     *
     * @param selector   元素选择器
     * @param timeout    超时时间（毫秒）
     * @param deviceType 设备类型
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator waitForElement(String selector, int timeout, DeviceType deviceType) {
        return waitForElement(DEFAULT_PLATFORM, selector, timeout, deviceType);
    }
    
    /**
     * 为特定平台查找元素并等待直到可见
     *
     * @param platformName 平台名称
     * @param selector     元素选择器
     * @param timeout      超时时间（毫秒）
     * @param deviceType   设备类型
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator waitForElement(String platformName, String selector, int timeout, DeviceType deviceType) {
        Page page = getPage(platformName, deviceType);
        if (page != null) {
            Locator locator = page.locator(selector);
            try {
                locator.waitFor(new Locator.WaitForOptions().setTimeout(timeout));
                return locator;
            } catch (Exception e) {
                log.error("等待元素失败: {} (平台: {}, 设备类型: {})", selector, platformName, deviceType, e);
            }
        }
        return null;
    }

    /**
     * 使用默认设备类型查找元素并等待直到可见
     *
     * @param selector 元素选择器
     * @param timeout  超时时间（毫秒）
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator waitForElement(String selector, int timeout) {
        return waitForElement(DEFAULT_PLATFORM, selector, timeout, defaultDeviceType);
    }
    
    /**
     * 为特定平台使用默认设备类型查找元素并等待直到可见
     *
     * @param platformName 平台名称
     * @param selector     元素选择器
     * @param timeout      超时时间（毫秒）
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator waitForElement(String platformName, String selector, int timeout) {
        return waitForElement(platformName, selector, timeout, defaultDeviceType);
    }

    /**
     * 使用默认超时时间和默认设备类型等待元素
     *
     * @param selector 元素选择器
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator waitForElement(String selector) {
        return waitForElement(DEFAULT_PLATFORM, selector, DEFAULT_WAIT_TIME, defaultDeviceType);
    }
    
    /**
     * 为特定平台使用默认超时时间和默认设备类型等待元素
     *
     * @param platformName 平台名称
     * @param selector     元素选择器
     * @return 元素对象，如果未找到则返回null
     */
    public static Locator waitForElement(String platformName, String selector) {
        return waitForElement(platformName, selector, DEFAULT_WAIT_TIME, defaultDeviceType);
    }

    /**
     * 点击元素
     *
     * @param selector   元素选择器
     * @param deviceType 设备类型
     */
    public static void click(String selector, DeviceType deviceType) {
        click(DEFAULT_PLATFORM, selector, deviceType);
    }
    
    /**
     * 为特定平台点击元素
     *
     * @param platformName 平台名称
     * @param selector     元素选择器
     * @param deviceType   设备类型
     */
    public static void click(String platformName, String selector, DeviceType deviceType) {
        try {
            Page page = getPage(platformName, deviceType);
            if (page != null) {
                page.locator(selector).click();
                log.info("已点击元素: {} (平台: {}, 设备类型: {})", selector, platformName, deviceType);
            }
        } catch (PlaywrightException e) {
            log.error("点击元素失败: {} (平台: {}, 设备类型: {})", selector, platformName, deviceType, e);
        }
    }

    /**
     * 使用默认设备类型点击元素
     *
     * @param selector 元素选择器
     */
    public static void click(String selector) {
        click(DEFAULT_PLATFORM, selector, defaultDeviceType);
    }
    
    /**
     * 为特定平台使用默认设备类型点击元素
     *
     * @param platformName 平台名称
     * @param selector     元素选择器
     */
    public static void click(String platformName, String selector) {
        click(platformName, selector, defaultDeviceType);
    }

    /**
     * 填写表单字段
     *
     * @param selector   元素选择器
     * @param text       要输入的文本
     * @param deviceType 设备类型
     */
    public static void fill(String selector, String text, DeviceType deviceType) {
        fill(DEFAULT_PLATFORM, selector, text, deviceType);
    }
    
    /**
     * 为特定平台填写表单字段
     *
     * @param platformName 平台名称
     * @param selector     元素选择器
     * @param text         要输入的文本
     * @param deviceType   设备类型
     */
    public static void fill(String platformName, String selector, String text, DeviceType deviceType) {
        try {
            Page page = getPage(platformName, deviceType);
            if (page != null) {
                page.locator(selector).fill(text);
                log.info("已在元素{}中输入文本 (平台: {}, 设备类型: {})", selector, platformName, deviceType);
            }
        } catch (PlaywrightException e) {
            log.error("填写表单失败: {} (平台: {}, 设备类型: {})", selector, platformName, deviceType, e);
        }
    }

    /**
     * 使用默认设备类型填写表单字段
     *
     * @param selector 元素选择器
     * @param text     要输入的文本
     */
    public static void fill(String selector, String text) {
        fill(DEFAULT_PLATFORM, selector, text, defaultDeviceType);
    }
    
    /**
     * 为特定平台使用默认设备类型填写表单字段
     *
     * @param platformName 平台名称
     * @param selector     元素选择器
     * @param text         要输入的文本
     */
    public static void fill(String platformName, String selector, String text) {
        fill(platformName, selector, text, defaultDeviceType);
    }

    /**
     * 模拟人类输入文本（逐字输入）
     *
     * @param selector   元素选择器
     * @param text       要输入的文本
     * @param minDelay   字符间最小延迟（毫秒）
     * @param maxDelay   字符间最大延迟（毫秒）
     * @param deviceType 设备类型
     */
    public static void typeHumanLike(String selector, String text, int minDelay, int maxDelay, DeviceType deviceType) {
        typeHumanLike(DEFAULT_PLATFORM, selector, text, minDelay, maxDelay, deviceType);
    }
    
    /**
     * 为特定平台模拟人类输入文本（逐字输入）
     *
     * @param platformName 平台名称
     * @param selector     元素选择器
     * @param text         要输入的文本
     * @param minDelay     字符间最小延迟（毫秒）
     * @param maxDelay     字符间最大延迟（毫秒）
     * @param deviceType   设备类型
     */
    public static void typeHumanLike(String platformName, String selector, String text, int minDelay, int maxDelay, DeviceType deviceType) {
        try {
            Page page = getPage(platformName, deviceType);
            if (page != null) {
                Locator locator = page.locator(selector);
                locator.click();

                Random random = new Random();
                for (char c : text.toCharArray()) {
                    // 计算本次字符输入的延迟时间
                    int delay = random.nextInt(maxDelay - minDelay + 1) + minDelay;

                    // 输入单个字符
                    locator.pressSequentially(String.valueOf(c),
                            new Locator.PressSequentiallyOptions().setDelay(delay));
                }
                log.info("已模拟人类在元素{}中输入文本 (平台: {}, 设备类型: {})", selector, platformName, deviceType);
            }
        } catch (PlaywrightException e) {
            log.error("模拟人类输入失败: {} (平台: {}, 设备类型: {})", selector, platformName, deviceType, e);
        }
    }

    /**
     * 使用默认设备类型模拟人类输入文本
     *
     * @param selector 元素选择器
     * @param text     要输入的文本
     * @param minDelay 字符间最小延迟（毫秒）
     * @param maxDelay 字符间最大延迟（毫秒）
     */
    public static void typeHumanLike(String selector, String text, int minDelay, int maxDelay) {
        typeHumanLike(selector, text, minDelay, maxDelay, defaultDeviceType);
    }

    /**
     * 获取元素文本
     *
     * @param selector   元素选择器
     * @param deviceType 设备类型
     * @return 元素文本内容
     */
    public static String getText(String selector, DeviceType deviceType) {
        try {
            return getPage(deviceType).locator(selector).textContent();
        } catch (PlaywrightException e) {
            log.error("获取元素文本失败: {} (设备类型: {})", selector, deviceType, e);
            return "";
        }
    }

    /**
     * 使用默认设备类型获取元素文本
     *
     * @param selector 元素选择器
     * @return 元素文本内容
     */
    public static String getText(String selector) {
        return getText(selector, defaultDeviceType);
    }

    /**
     * 获取元素属性值
     *
     * @param selector      元素选择器
     * @param attributeName 属性名
     * @param deviceType    设备类型
     * @return 属性值
     */
    public static String getAttribute(String selector, String attributeName, DeviceType deviceType) {
        try {
            return getPage(deviceType).locator(selector).getAttribute(attributeName);
        } catch (PlaywrightException e) {
            log.error("获取元素属性失败: {}[{}] (设备类型: {})", selector, attributeName, deviceType, e);
            return "";
        }
    }

    /**
     * 使用默认设备类型获取元素属性值
     *
     * @param selector      元素选择器
     * @param attributeName 属性名
     * @return 属性值
     */
    public static String getAttribute(String selector, String attributeName) {
        return getAttribute(selector, attributeName, defaultDeviceType);
    }

    /**
     * 截取页面截图并保存
     *
     * @param path       保存路径
     * @param deviceType 设备类型
     */
    public static void screenshot(String path, DeviceType deviceType) {
        try {
            getPage(deviceType).screenshot(new Page.ScreenshotOptions().setPath(Paths.get(path)));
            log.info("已保存截图到: {} (设备类型: {})", path, deviceType);
        } catch (PlaywrightException e) {
            log.error("截图失败 (设备类型: {})", deviceType, e);
        }
    }

    /**
     * 使用默认设备类型截取页面截图并保存
     *
     * @param path 保存路径
     */
    public static void screenshot(String path) {
        screenshot(path, defaultDeviceType);
    }

    /**
     * 截取特定元素的截图
     *
     * @param selector   元素选择器
     * @param path       保存路径
     * @param deviceType 设备类型
     */
    public static void screenshotElement(String selector, String path, DeviceType deviceType) {
        try {
            getPage(deviceType).locator(selector).screenshot(new Locator.ScreenshotOptions().setPath(Paths.get(path)));
            log.info("已保存元素截图到: {} (设备类型: {})", path, deviceType);
        } catch (PlaywrightException e) {
            log.error("元素截图失败: {} (设备类型: {})", selector, deviceType, e);
        }
    }

    /**
     * 使用默认设备类型截取特定元素的截图
     *
     * @param selector 元素选择器
     * @param path     保存路径
     */
    public static void screenshotElement(String selector, String path) {
        screenshotElement(selector, path, defaultDeviceType);
    }

    /**
     * 保存Cookie到文件
     *
     * @param path       保存路径
     * @param deviceType 设备类型
     */
    public static void saveCookies(String path, DeviceType deviceType) {
        try {
            List<Cookie> cookies = getContext(deviceType).cookies();
            JSONArray jsonArray = new JSONArray();

            for (Cookie cookie : cookies) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", cookie.name);
                jsonObject.put("value", cookie.value);
                jsonObject.put("domain", cookie.domain);
                jsonObject.put("path", cookie.path);
                if (cookie.expires != null) {
                    jsonObject.put("expires", cookie.expires);
                }
                jsonObject.put("secure", cookie.secure);
                jsonObject.put("httpOnly", cookie.httpOnly);
                jsonArray.put(jsonObject);
            }

            try (FileWriter file = new FileWriter(path)) {
                file.write(jsonArray.toString(4));
                log.info("Cookie已保存到文件: {} (设备类型: {})", path, deviceType);
            }
        } catch (IOException e) {
            log.error("保存Cookie失败 (设备类型: {})", deviceType, e);
        }
    }

    /**
     * 使用默认设备类型保存Cookie到文件
     *
     * @param path 保存路径
     */
    public static void saveCookies(String path) {
        saveCookies(path, defaultDeviceType);
    }
    
    /**
     * 为特定平台和默认设备类型保存Cookie到文件
     *
     * @param path 保存路径
     * @param platformName 平台名称
     */
    public static void saveCookies(String path, String platformName) {
        try {
            List<Cookie> cookies = getContext(platformName, defaultDeviceType).cookies();
            JSONArray jsonArray = new JSONArray();

            for (Cookie cookie : cookies) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", cookie.name);
                jsonObject.put("value", cookie.value);
                jsonObject.put("domain", cookie.domain);
                jsonObject.put("path", cookie.path);
                if (cookie.expires != null) {
                    jsonObject.put("expires", cookie.expires);
                }
                jsonObject.put("secure", cookie.secure);
                jsonObject.put("httpOnly", cookie.httpOnly);
                jsonArray.put(jsonObject);
            }

            try (FileWriter file = new FileWriter(path)) {
                file.write(jsonArray.toString(4));
                log.info("Cookie已保存到文件: {} (平台: {}, 设备类型: {})", path, platformName, defaultDeviceType);
            }
        } catch (IOException e) {
            log.error("保存Cookie失败 (平台: {}, 设备类型: {})", platformName, defaultDeviceType, e);
        }
    }

    /**
     * 从文件加载Cookie
     *
     * @param path       Cookie文件路径
     * @param deviceType 设备类型
     */
    public static void loadCookies(String path, DeviceType deviceType) {
        try {
            String jsonText = new String(Files.readAllBytes(Paths.get(path)));
            JSONArray jsonArray = new JSONArray(jsonText);

            List<com.microsoft.playwright.options.Cookie> cookies = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                com.microsoft.playwright.options.Cookie cookie = new com.microsoft.playwright.options.Cookie(
                        jsonObject.getString("name"),
                        jsonObject.getString("value"));

                if (!jsonObject.isNull("domain")) {
                    cookie.domain = jsonObject.getString("domain");
                }

                if (!jsonObject.isNull("path")) {
                    cookie.path = jsonObject.getString("path");
                }

                if (!jsonObject.isNull("expires")) {
                    cookie.expires = jsonObject.getDouble("expires");
                }

                if (!jsonObject.isNull("secure")) {
                    cookie.secure = jsonObject.getBoolean("secure");
                }

                if (!jsonObject.isNull("httpOnly")) {
                    cookie.httpOnly = jsonObject.getBoolean("httpOnly");
                }

                cookies.add(cookie);
            }

            getContext(deviceType).addCookies(cookies);
            log.info("已从文件加载Cookie: {} (设备类型: {})", path, deviceType);
        } catch (IOException e) {
            log.error("加载Cookie失败 (设备类型: {})", deviceType, e);
        }
    }

    /**
     * 使用默认设备类型从文件加载Cookie
     *
     * @param path Cookie文件路径
     */
    public static void loadCookies(String path) {
        loadCookies(path, defaultDeviceType);
    }
    
    /**
     * 为特定平台从文件加载Cookie
     *
     * @param path Cookie文件路径
     * @param platformName 平台名称
     */
    public static void loadCookies(String path, String platformName) {
        loadCookies(path, platformName, defaultDeviceType);
    }
    
    /**
     * 为特定平台和设备类型从文件加载Cookie
     *
     * @param path Cookie文件路径
     * @param platformName 平台名称
     * @param deviceType 设备类型
     */
    public static void loadCookies(String path, String platformName, DeviceType deviceType) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(path)));
            JSONArray jsonArray = new JSONArray(content);
            List<Cookie> cookies = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                Cookie cookie = new Cookie(jsonObject.getString("name"), jsonObject.getString("value"));

                if (!jsonObject.isNull("domain")) {
                    cookie.domain = jsonObject.getString("domain");
                }

                if (!jsonObject.isNull("path")) {
                    cookie.path = jsonObject.getString("path");
                }

                if (!jsonObject.isNull("expires")) {
                    cookie.expires = jsonObject.getDouble("expires");
                }

                if (!jsonObject.isNull("secure")) {
                    cookie.secure = jsonObject.getBoolean("secure");
                }

                if (!jsonObject.isNull("httpOnly")) {
                    cookie.httpOnly = jsonObject.getBoolean("httpOnly");
                }

                cookies.add(cookie);
            }

            getContext(platformName, deviceType).addCookies(cookies);
            log.info("已从文件加载Cookie: {} (平台: {}, 设备类型: {})", path, platformName, deviceType);
        } catch (IOException e) {
            log.error("加载Cookie失败 (平台: {}, 设备类型: {})", platformName, deviceType, e);
        }
    }

    /**
     * 执行JavaScript代码
     *
     * @param script     JavaScript代码
     * @param deviceType 设备类型
     */
    public static void evaluate(String script, DeviceType deviceType) {
        try {
            getPage(deviceType).evaluate(script);
        } catch (PlaywrightException e) {
            log.error("执行JavaScript失败 (设备类型: {})", deviceType, e);
        }
    }

    /**
     * 使用默认设备类型执行JavaScript代码
     *
     * @param script JavaScript代码
     */
    public static void evaluate(String script) {
        evaluate(script, defaultDeviceType);
    }

    /**
     * 等待页面加载完成
     *
     * @param deviceType 设备类型
     */
    public static void waitForPageLoad(DeviceType deviceType) {
        getPage(deviceType).waitForLoadState(LoadState.DOMCONTENTLOADED);
        getPage(deviceType).waitForLoadState(LoadState.NETWORKIDLE);
    }

    /**
     * 检查元素是否可见
     *
     * @param selector   元素选择器
     * @param deviceType 设备类型
     * @return 是否可见
     */
    public static boolean elementIsVisible(String selector, DeviceType deviceType) {
        try {
            return getPage(deviceType).locator(selector).isVisible();
        } catch (PlaywrightException e) {
            return false;
        }
    }

    /**
     * 使用默认设备类型检查元素是否可见
     *
     * @param selector 元素选择器
     * @return 是否可见
     */
    public static boolean elementIsVisible(String selector) {
        return elementIsVisible(selector, defaultDeviceType);
    }

    /**
     * 选择下拉列表选项（通过文本）
     *
     * @param selector   选择器
     * @param optionText 选项文本
     * @param deviceType 设备类型
     */
    public static void selectByText(String selector, String optionText, DeviceType deviceType) {
        getPage(deviceType).locator(selector).selectOption(new SelectOption().setLabel(optionText));
    }

    /**
     * 使用默认设备类型选择下拉列表选项（通过文本）
     *
     * @param selector   选择器
     * @param optionText 选项文本
     */
    public static void selectByText(String selector, String optionText) {
        selectByText(selector, optionText, defaultDeviceType);
    }

    /**
     * 选择下拉列表选项（通过值）
     *
     * @param selector   选择器
     * @param value      选项值
     * @param deviceType 设备类型
     */
    public static void selectByValue(String selector, String value, DeviceType deviceType) {
        getPage(deviceType).locator(selector).selectOption(new SelectOption().setValue(value));
    }

    /**
     * 使用默认设备类型选择下拉列表选项（通过值）
     *
     * @param selector 选择器
     * @param value    选项值
     */
    public static void selectByValue(String selector, String value) {
        selectByValue(selector, value, defaultDeviceType);
    }

    /**
     * 获取当前页面标题
     *
     * @param deviceType 设备类型
     * @return 页面标题
     */
    public static String getTitle(DeviceType deviceType) {
        return getPage(deviceType).title();
    }

    /**
     * 使用默认设备类型获取当前页面标题
     *
     * @return 页面标题
     */
    public static String getTitle() {
        return getTitle(defaultDeviceType);
    }

    /**
     * 获取当前页面URL
     *
     * @param deviceType 设备类型
     * @return 页面URL
     */
    public static String getUrl(DeviceType deviceType) {
        return getPage(deviceType).url();
    }

    /**
     * 使用默认设备类型获取当前页面URL
     *
     * @return 页面URL
     */
    public static String getUrl() {
        return getUrl(defaultDeviceType);
    }

    /**
     * 初始化Stealth模式（使浏览器更难被检测为自动化工具）
     * 增强版本，集成SeleniumUtil的反检测功能
     *
     * @param deviceType 设备类型
     */
    public static void initStealth(DeviceType deviceType) {
        // 获取当前页面，不重新创建上下文和页面
        Page page = getPage(deviceType);
        
        // 为现有上下文设置额外的HTTP头
        BrowserContext context = getContext(deviceType);
        if (deviceType == DeviceType.DESKTOP) {
            context.setExtraHTTPHeaders(Map.of(
                    "sec-ch-ua", "\"Google Chrome\";v=\"135\", \"Not-A.Brand\";v=\"8\", \"Chromium\";v=\"135\"",
                    "sec-ch-ua-mobile", "?0",
                    "sec-ch-ua-platform", "\"macOS\"",
                    "accept-language", "zh-CN,zh;q=0.9",
                    "referer", "https://www.zhipin.com/",
                    "sec-fetch-dest", "document",
                    "sec-fetch-mode", "navigate",
                    "sec-fetch-site", "same-origin"));
        } else {
            context.setExtraHTTPHeaders(Map.of(
                    "sec-ch-ua", "\"Chromium\";v=\"135\", \"Not A(Brand\";v=\"99\"",
                    "sec-ch-ua-mobile", "?1",
                    "sec-ch-ua-platform", "\"iOS\"",
                    "accept-language", "zh-CN,zh;q=0.9",
                    "sec-fetch-dest", "document",
                    "sec-fetch-mode", "navigate",
                    "sec-fetch-site", "same-origin"));
        }

        // 注入反检测脚本（从SeleniumUtil移植）
        String stealthScript = """
                Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_JSON;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Object;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Proxy;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Window;
                window.navigator.chrome = { runtime: {} };
                Object.defineProperty(navigator, 'languages', {get: () => ['zh-CN', 'zh']});
                Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3]});
                Object.defineProperty(navigator, 'injected', {get: () => 123});
                """;
        
        page.addInitScript(stealthScript);

        // 如果有stealth.min.js文件，也尝试加载
        try {
            String stealthJs = new String(
                    Files.readAllBytes(Paths.get("src/main/resources/stealth.min.js")));
            page.addInitScript(stealthJs);
            log.info("已加载stealth.min.js文件");
        } catch (IOException e) {
            log.info("未找到stealth.min.js文件，使用内置反检测脚本");
        }
        log.info("已启用增强Stealth模式 (设备类型: {})", deviceType);
    }

    /**
     * 使用默认设备类型初始化Stealth模式
     */
    public static void initStealth() {
        initStealth(defaultDeviceType);
    }
    
    /**
     * 为特定平台初始化Stealth模式
     *
     * @param platformName 平台名称
     */
    public static void initStealth(String platformName) {
        initStealth(platformName, defaultDeviceType);
    }
    
    /**
     * 为特定平台和设备类型初始化Stealth模式
     *
     * @param platformName 平台名称
     * @param deviceType 设备类型
     */
    public static void initStealth(String platformName, DeviceType deviceType) {
        // 获取当前页面，不重新创建上下文和页面
        Page page = getPage(platformName, deviceType);
        if (page == null) {
            log.error("未找到页面实例，无法初始化Stealth模式 (平台: {}, 设备类型: {})", platformName, deviceType);
            return;
        }
        
        // 为现有上下文设置额外的HTTP头
        BrowserContext context = getContext(platformName, deviceType);
        if (context == null) {
            log.error("未找到上下文实例，无法初始化Stealth模式 (平台: {}, 设备类型: {})", platformName, deviceType);
            return;
        }
        
        if (deviceType == DeviceType.DESKTOP) {
            context.setExtraHTTPHeaders(Map.of(
                    "sec-ch-ua", "\"Google Chrome\";v=\"135\", \"Not-A.Brand\";v=\"8\", \"Chromium\";v=\"135\"",
                    "sec-ch-ua-mobile", "?0",
                    "sec-ch-ua-platform", "\"macOS\"",
                    "accept-language", "zh-CN,zh;q=0.9",
                    "referer", "https://www.zhipin.com/",
                    "sec-fetch-dest", "document",
                    "sec-fetch-mode", "navigate",
                    "sec-fetch-site", "same-origin"));
        } else {
            context.setExtraHTTPHeaders(Map.of(
                    "sec-ch-ua", "\"Chromium\";v=\"135\", \"Not A(Brand\";v=\"99\"",
                    "sec-ch-ua-mobile", "?1",
                    "sec-ch-ua-platform", "\"iOS\"",
                    "accept-language", "zh-CN,zh;q=0.9",
                    "sec-fetch-dest", "document",
                    "sec-fetch-mode", "navigate",
                    "sec-fetch-site", "same-origin"));
        }

        // 注入反检测脚本
        String stealthScript = """
                Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_JSON;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Object;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Proxy;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;
                delete window.cdc_adoQpoasnfa76pfcZLmcfl_Window;
                window.navigator.chrome = { runtime: {} };
                Object.defineProperty(navigator, 'languages', {get: () => ['zh-CN', 'zh']});
                Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3]});
                Object.defineProperty(navigator, 'injected', {get: () => 123});
                """;
        
        page.addInitScript(stealthScript);

        // 如果有stealth.min.js文件，也尝试加载
        try {
            String stealthJs = new String(
                    Files.readAllBytes(Paths.get("src/main/resources/stealth.min.js")));
            page.addInitScript(stealthJs);
            log.info("已加载stealth.min.js文件");
        } catch (IOException e) {
            log.info("未找到stealth.min.js文件，使用内置反检测脚本");
        }
        log.info("已启用增强Stealth模式 (平台: {}, 设备类型: {})", platformName, deviceType);
    }

    /**
     * 设置默认请求头（从SeleniumUtil移植）
     *
     * @param deviceType 设备类型
     */
    public static void setDefaultHeaders(DeviceType deviceType) {
        BrowserContext context = getContext(deviceType);
        
        Map<String, String> headers = Map.of(
                "sec-ch-ua", "\"Google Chrome\";v=\"135\", \"Not-A.Brand\";v=\"8\", \"Chromium\";v=\"135\"",
                "sec-ch-ua-mobile", deviceType == DeviceType.MOBILE ? "?1" : "?0",
                "sec-ch-ua-platform", deviceType == DeviceType.MOBILE ? "\"iOS\"" : "\"macOS\"",
                "user-agent", deviceType == DeviceType.MOBILE ? 
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1" :
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
                "accept-language", "zh-CN,zh;q=0.9",
                "referer", "https://www.zhipin.com/"
        );
        
        context.setExtraHTTPHeaders(headers);
        log.info("已设置默认请求头 (设备类型: {})", deviceType);
    }

    /**
     * 使用默认设备类型设置默认请求头
     */
    public static void setDefaultHeaders() {
        setDefaultHeaders(defaultDeviceType);
    }

    /**
     * 获取特定平台和设备类型的Page对象
     *
     * @param platformName 平台名称
     * @param deviceType 设备类型
     * @return 对应的Page对象
     */
    public static Page getPageObject(String platformName, DeviceType deviceType) {
        return getPage(platformName, deviceType);
    }
    
    /**
     * 获取特定平台和默认设备类型的Page对象
     *
     * @param platformName 平台名称
     * @return 对应的Page对象
     */
    public static Page getPageObject(String platformName) {
        return getPage(platformName, defaultDeviceType);
    }

    /**
     * 获取默认平台和指定设备类型的Page对象（兼容旧代码）
     *
     * @param deviceType 设备类型
     * @return 对应的Page对象
     */
    public static Page getPageObject(DeviceType deviceType) {
        return getPage(DEFAULT_PLATFORM, deviceType);
    }

    /**
     * 使用默认平台和默认设备类型获取Page对象（兼容旧代码）
     *
     * @return 对应的Page对象
     */
    public static Page getPageObject() {
        // 注意：这里为了兼容旧代码，仍然返回默认平台的页面
        // 但在实际使用中，各平台应该调用带平台名称的方法
        return getPage(DEFAULT_PLATFORM, defaultDeviceType);
    }

    /**
     * 设置自定义Cookie
     *
     * @param name       Cookie名称
     * @param value      Cookie值
     * @param domain     Cookie域
     * @param path       Cookie路径
     * @param expires    过期时间（可选）
     * @param secure     是否安全（可选）
     * @param httpOnly   是否仅HTTP（可选）
     * @param deviceType 设备类型
     */
    public static void setCookie(String name, String value, String domain, String path,
                                 Double expires, Boolean secure, Boolean httpOnly, DeviceType deviceType) {
        com.microsoft.playwright.options.Cookie cookie = new com.microsoft.playwright.options.Cookie(name, value);
        cookie.domain = domain;
        cookie.path = path;

        if (expires != null) {
            cookie.expires = expires;
        }

        if (secure != null) {
            cookie.secure = secure;
        }

        if (httpOnly != null) {
            cookie.httpOnly = httpOnly;
        }

        List<com.microsoft.playwright.options.Cookie> cookies = new ArrayList<>();
        cookies.add(cookie);

        getContext(deviceType).addCookies(cookies);
        log.info("已设置Cookie: {} (设备类型: {})", name, deviceType);
    }

    /**
     * 使用默认设备类型设置自定义Cookie
     *
     * @param name     Cookie名称
     * @param value    Cookie值
     * @param domain   Cookie域
     * @param path     Cookie路径
     * @param expires  过期时间（可选）
     * @param secure   是否安全（可选）
     * @param httpOnly 是否仅HTTP（可选）
     */
    public static void setCookie(String name, String value, String domain, String path,
                                 Double expires, Boolean secure, Boolean httpOnly) {
        setCookie(name, value, domain, path, expires, secure, httpOnly, defaultDeviceType);
    }

    /**
     * 简化的设置Cookie方法
     *
     * @param name       Cookie名称
     * @param value      Cookie值
     * @param domain     Cookie域
     * @param path       Cookie路径
     * @param deviceType 设备类型
     */
    public static void setCookie(String name, String value, String domain, String path, DeviceType deviceType) {
        setCookie(name, value, domain, path, null, null, null, deviceType);
    }

    /**
     * 使用默认设备类型的简化设置Cookie方法
     *
     * @param name   Cookie名称
     * @param value  Cookie值
     * @param domain Cookie域
     * @param path   Cookie路径
     */
    public static void setCookie(String name, String value, String domain, String path) {
        setCookie(name, value, domain, path, null, null, null, defaultDeviceType);
    }

    /**
     * 检查Cookie文件是否有效（从SeleniumUtil移植）
     *
     * @param cookiePath Cookie文件路径
     * @return 文件是否存在
     */
    public static boolean isCookieValid(String cookiePath) {
        return Files.exists(Paths.get(cookiePath));
    }

    /**
     * 带错误消息的元素查找（从SeleniumUtil移植）
     *
     * @param selector    元素选择器
     * @param message     错误消息
     * @param deviceType  设备类型
     * @return 元素对象的Optional包装
     */
    public static Optional<Locator> findElementWithMessage(String selector, String message, DeviceType deviceType) {
        try {
            Locator locator = getPage(deviceType).locator(selector);
            // 检查元素是否存在
            if (locator.count() > 0) {
                return Optional.of(locator);
            } else {
                log.error(message);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error(message + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 使用默认设备类型的带错误消息的元素查找
     *
     * @param selector 元素选择器
     * @param message  错误消息
     * @return 元素对象的Optional包装
     */
    public static Optional<Locator> findElementWithMessage(String selector, String message) {
        return findElementWithMessage(selector, message, defaultDeviceType);
    }
}