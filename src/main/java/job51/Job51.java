package job51;

import lombok.SneakyThrows;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JobUtils;
import utils.SeleniumUtil;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static utils.Bot.sendMessageByTime;
import static utils.Constant.*;
import static utils.JobUtils.formatDuration;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * 前程无忧自动投递简历
 */
public class Job51 {
    static {
        // 在类加载时就设置日志文件名，确保Logger初始化时能获取到正确的属性
        System.setProperty("log.name", "job51");
    }
    
    private static final Logger log = LoggerFactory.getLogger(Job51.class);

    static Integer page = 1;
    static Integer maxPage = 50;
    static String cookiePath = "./src/main/java/job51/cookie.json";
    static String dataPath = "./src/main/java/job51/data.json";
    static String homeUrl = "https://www.51job.com";
    static String loginUrl = "https://login.51job.com/login.php?lang=c&url=https://www.51job.com/&qrlogin=2";
    static String baseUrl = "https://we.51job.com/pc/search?";
    static List<String> resultList = new ArrayList<>();
    static Job51Config config = Job51Config.init();
    static Date startDate;
    static List<String> blackCompanies = new ArrayList<>();
    static List<String> blackJobs = new ArrayList<>();
    static List<String> blackRecruiters = new ArrayList<>();

    public static void main(String[] args) {
        log.info("51job投递任务开始");
        try {
            // 加载过滤规则
            loadFilterRules();
            
            String searchUrl = getSearchUrl();
            // 使用带平台名称的浏览器初始化方法
            SeleniumUtil.initDriver("job51");
            startDate = new Date();
            Login();
            config.getKeywords().forEach(keyword -> resume(searchUrl + "&keyword=" + keyword));
            printResult();
        } catch (Exception e) {
            log.error("51job投递任务发生异常: {}", e.getMessage(), e);
            printResult();
        } finally {
            // 关闭该平台的浏览器实例
            SeleniumUtil.closePlatform("job51");
        }
    }
    
    /**
     * 加载过滤规则，从data.json文件中读取黑名单信息
     */
    private static void loadFilterRules() {
        try {
            File file = new File(dataPath);
            if (!file.exists()) {
                log.warn("过滤规则文件不存在: {}", dataPath);
                return;
            }
            
            StringBuilder content = new StringBuilder();
            try (FileReader reader = new FileReader(file)) {
                char[] buffer = new char[1024];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    content.append(buffer, 0, read);
                }
            }
            
            JSONObject json = new JSONObject(content.toString());
            
            // 加载黑名单公司
            JSONArray companies = json.optJSONArray("blackCompanies");
            if (companies != null) {
                for (int i = 0; i < companies.length(); i++) {
                    blackCompanies.add(companies.getString(i).toLowerCase());
                }
            }
            
            // 加载黑名单职位
            JSONArray jobs = json.optJSONArray("blackJobs");
            if (jobs != null) {
                for (int i = 0; i < jobs.length(); i++) {
                    blackJobs.add(jobs.getString(i).toLowerCase());
                }
            }
            
            // 加载黑名单招聘者
            JSONArray recruiters = json.optJSONArray("blackRecruiters");
            if (recruiters != null) {
                for (int i = 0; i < recruiters.length(); i++) {
                    blackRecruiters.add(recruiters.getString(i).toLowerCase());
                }
            }
            
            log.info("成功加载过滤规则，共 {} 个黑名单公司，{} 个黑名单职位，{} 个黑名单招聘者", 
                    blackCompanies.size(), blackJobs.size(), blackRecruiters.size());
        } catch (Exception e) {
            log.error("加载过滤规则失败: {}", e.getMessage());
        }
    }
    
    /**
     * 检查是否需要过滤该职位
     * @param company 公司名
     * @param title 职位名
     * @param recruiter 招聘者信息（可选）
     * @return 是否需要过滤
     */
    private static boolean shouldFilter(String company, String title, String recruiter) {
        // 转换为小写进行匹配，忽略大小写
        String companyLower = company.toLowerCase();
        String titleLower = title.toLowerCase();
        
        // 检查公司名是否在黑名单中
        for (String blackCompany : blackCompanies) {
            if (companyLower.contains(blackCompany)) {
                log.info("跳过黑名单公司: {}", company);
                return true;
            }
        }
        
        // 检查职位名是否在黑名单中
        for (String blackJob : blackJobs) {
            if (titleLower.contains(blackJob)) {
                log.info("跳过黑名单职位: {}", title);
                return true;
            }
        }
        
        // 检查招聘者是否在黑名单中（如果提供了招聘者信息）
        if (isNotNullOrEmpty(recruiter)) {
            String recruiterLower = recruiter.toLowerCase();
            for (String blackRecruiter : blackRecruiters) {
                if (recruiterLower.contains(blackRecruiter)) {
                    log.info("跳过黑名单招聘者: {}", recruiter);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 检查是否需要过滤该职位（兼容旧调用方式）
     * @param company 公司名
     * @param title 职位名
     * @return 是否需要过滤
     */
    private static boolean shouldFilter(String company, String title) {
        return shouldFilter(company, title, null);
    }

    private static void printResult() {
        try {
            String message = String.format("\n51job投递完成，共投递%d个简历，用时%s", resultList.size(), formatDuration(startDate, new Date()));
            log.info(message);
            sendMessageByTime(message);
            resultList.clear();
            
            // 安全关闭浏览器
            if (CHROME_DRIVER != null) {
                try {
                    CHROME_DRIVER.close();
                } catch (Exception e) {
                    log.error("关闭浏览器标签页失败: {}", e.getMessage());
                }
                try {
                    CHROME_DRIVER.quit();
                } catch (Exception e) {
                    log.error("退出浏览器失败: {}", e.getMessage());
                }
            }
            
            // 确保所有日志都被刷新到文件
            try {
                Thread.sleep(1000); // 等待1秒确保日志写入完成
                // 强制刷新日志 - 使用正确的方法
                ch.qos.logback.classic.LoggerContext loggerContext = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
                loggerContext.stop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            log.error("打印结果时发生异常: {}", e.getMessage(), e);
        }
    }

    private static String getSearchUrl() {
        return baseUrl +
                JobUtils.appendListParam("jobArea", config.getJobArea()) +
                JobUtils.appendListParam("salary", config.getSalary());
    }

    private static void Login() {
        CHROME_DRIVER.get(homeUrl);
        if (SeleniumUtil.isCookieValid(cookiePath)) {
            SeleniumUtil.loadCookie("job51", cookiePath);
            CHROME_DRIVER.navigate().refresh();
            SeleniumUtil.sleep(1);
        }
        if (isLoginRequired()) {
            log.error("cookie失效，尝试扫码登录...");
            scanLogin();
        }
    }

    private static boolean isLoginRequired() {
        try {
            String text = CHROME_DRIVER.findElement(By.cssSelector("span.login")).getText();
            return text != null && text.contains("登录");
        } catch (Exception e) {
            log.info("cookie有效，已登录...");
            return false;
        }
    }

    @SneakyThrows
    private static void resume(String url) {
        CHROME_DRIVER.get(url);
        SeleniumUtil.sleep(1);

        // 再次判断是否登录
        WebElement login = WAIT.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//a[contains(@class, 'uname')]")));
        if (login != null && isNotNullOrEmpty(login.getText()) && login.getText().contains("登录")) {
            login.click();
            WAIT.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//i[contains(@class, 'passIcon')]"))).click();
            log.info("请扫码登录...");
            WAIT.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[contains(@class, 'joblist')]")));
            SeleniumUtil.saveCookie("job51", cookiePath);
        }

        //由于51更新，每投递一页之前，停止10秒
        SeleniumUtil.sleep(10);

        int i = 0;
        try {
            CHROME_DRIVER.findElements(By.className("ss")).get(i).click();
        } catch (Exception e) {
            findAnomaly();
        }
        for (int j = page; j <= maxPage; j++) {
            while (true) {
                try {
                    WebElement mytxt = WAIT.until(ExpectedConditions.elementToBeClickable(By.id("jump_page")));
                    // 确保输入框位于视口并聚焦
                    ((JavascriptExecutor) CHROME_DRIVER).executeScript("arguments[0].scrollIntoView({block:'center'});", mytxt);
                    SeleniumUtil.sleep(1);
                    mytxt.click();

                    // 使用组合键清空，避免直接 clear 失败
                    mytxt.sendKeys(Keys.chord(Keys.CONTROL, "a"));
                    mytxt.sendKeys(Keys.BACK_SPACE);

                    // 输入页码
                    mytxt.sendKeys(String.valueOf(j));

                    // 使用 JS 点击跳页按钮，规避遮挡/不可点击
                    WebElement jumpBtn = WAIT.until(ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div.bottom-page > div > div > span.jumpPage")));
                    ((JavascriptExecutor) CHROME_DRIVER).executeScript("arguments[0].click();", jumpBtn);

                    // 回到页面顶部
                    ACTIONS.keyDown(Keys.CONTROL).sendKeys(Keys.HOME).keyUp(Keys.CONTROL).perform();
                    log.info("第 {} 页", j);
                    break;
                } catch (Exception e) {
                    log.error("分页跳转失败，1秒后重试... {}", e.getMessage());
                    SeleniumUtil.sleep(1);
                    findAnomaly();
                    CHROME_DRIVER.navigate().refresh();
                }
            }
            postCurrentJob();
        }
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isBlank();
    }

    public static boolean isNotNullOrEmpty(String str) {
        return !isNullOrEmpty(str);
    }


    @SneakyThrows
    private static void postCurrentJob() {
        SeleniumUtil.sleep(3); // 增加等待时间，确保页面完全加载
        // 选择所有岗位，批量投递
        List<WebElement> checkboxes = CHROME_DRIVER.findElements(By.cssSelector("div.ick"));
        if (checkboxes.isEmpty()) {
            log.info("当前页面没有找到可选择的岗位");
            return;
        }
        List<WebElement> titles = CHROME_DRIVER.findElements(By.cssSelector("[class*='jname text-cut']"));
        List<WebElement> companies = CHROME_DRIVER.findElements(By.cssSelector("[class*='cname text-cut']"));
        JavascriptExecutor executor = CHROME_DRIVER;
        // 获取招聘者信息列表
        List<WebElement> recruiters = new ArrayList<>();
        try {
            // 尝试多种可能的招聘者选择器
            String[] recruiterSelectors = {
                "[class*='er']",       // 可能的招聘者元素类名
                ".er",                 // 直接类名
                "[class*='recruiter']", // 包含recruiter的类名
                ".j_cont > .er"        // 可能的嵌套结构
            };
            
            for (String selector : recruiterSelectors) {
                List<WebElement> foundRecruiters = CHROME_DRIVER.findElements(By.cssSelector(selector));
                if (!foundRecruiters.isEmpty()) {
                    recruiters = foundRecruiters;
                    log.info("成功获取招聘者信息，共 {} 个", recruiters.size());
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("获取招聘者信息时发生异常: {}", e.getMessage());
        }
        
        for (int i = 0; i < checkboxes.size() && i < titles.size() && i < companies.size(); i++) {
            WebElement checkbox = checkboxes.get(i);
            String title = titles.get(i).getText();
            String company = companies.get(i).getText();
            
            // 获取招聘者信息（如果有）
            String recruiter = "";
            if (i < recruiters.size()) {
                try {
                    recruiter = recruiters.get(i).getText();
                } catch (Exception e) {
                    log.debug("获取第 {} 个职位的招聘者信息失败", i);
                }
            }
            
            // 检查是否需要过滤该职位
            if (shouldFilter(company, title, recruiter)) {
                continue;
            }
            
            // 先滚动到复选框位置
            executor.executeScript("arguments[0].scrollIntoView({block:'center'});", checkbox);
            SeleniumUtil.sleep(1);
            // 使用JS点击，避免直接点击可能失败的问题
            executor.executeScript("arguments[0].click();", checkbox);
            resultList.add(company + " | " + title);
            log.info("选中:{} | {} 职位", company, title);
        }
        SeleniumUtil.sleep(2); // 增加等待时间
        // 回到页面顶部
        executor.executeScript("window.scrollTo({top: 0, behavior: 'smooth'});");
        SeleniumUtil.sleep(2);
        
        boolean success = false;
        int retryCount = 0;
        int maxRetries = 10;
        
        while (!success && retryCount < maxRetries) {
            retryCount++;
            try {
                log.info("尝试第 {} 次点击批量投递按钮", retryCount);
                
                // 策略1：尝试直接通过CSS选择器找到投递按钮
                WebElement batchBtn = null;
                try {
                    // 尝试多种可能的选择器
                    List<String> selectors = new ArrayList<>();
                    selectors.add("button.p_but:nth-child(2)"); // 原选择器
                    selectors.add("button.p_but:contains('投递')"); // 尝试包含投递文字的按钮
                    selectors.add("button[class*='p_but']"); // 尝试所有p_but类的按钮
                    selectors.add("div.tabs_in button"); // 尝试tabs_in下的所有按钮
                    selectors.add("//button[contains(text(), '投递')]"); // 尝试XPath
                    
                    for (String selector : selectors) {
                        try {
                            if (selector.startsWith("//")) {
                                // 使用XPath
                                batchBtn = CHROME_DRIVER.findElement(By.xpath(selector));
                            } else {
                                // 使用CSS选择器
                                List<WebElement> buttons = CHROME_DRIVER.findElements(By.cssSelector(selector));
                                if (buttons.size() > 1) {
                                    batchBtn = buttons.get(1); // 选择第二个按钮作为投递按钮
                                } else if (!buttons.isEmpty()) {
                                    batchBtn = buttons.get(0); // 如果只有一个，就选第一个
                                }
                            }
                            
                            if (batchBtn != null) {
                                log.info("找到投递按钮: {}", selector);
                                break;
                            }
                        } catch (Exception e) {
                            // 这个选择器失败，尝试下一个
                            continue;
                        }
                    }
                } catch (Exception e) {
                    log.warn("直接查找投递按钮失败: {}", e.getMessage());
                }
                
                // 如果上面的方法没找到，尝试原有的方法
                if (batchBtn == null) {
                    try {
                        WebElement parent = CHROME_DRIVER.findElement(By.cssSelector("div.tabs_in"));
                        List<WebElement> buttons = parent.findElements(By.cssSelector("button.p_but"));
                        if (buttons != null && !buttons.isEmpty()) {
                            batchBtn = buttons.get(1); // 获取第二个按钮
                            log.info("通过父元素找到投递按钮");
                        }
                    } catch (Exception e) {
                        log.warn("通过父元素查找投递按钮失败: {}", e.getMessage());
                    }
                }
                
                if (batchBtn != null) {
                    // 滚动到按钮位置，确保可见
                    executor.executeScript("arguments[0].scrollIntoView({block:'center'});", batchBtn);
                    SeleniumUtil.sleep(1);
                    
                    // 尝试多种点击方式
                    try {
                        // 方法1：使用WebDriverWait等待按钮可点击
                        try {
                            WAIT.until(ExpectedConditions.elementToBeClickable(batchBtn));
                            log.info("按钮可点击");
                        } catch (Exception e) {
                            log.warn("等待按钮可点击失败，继续尝试点击: {}", e.getMessage());
                        }
                        
                        // 方法2：使用JS直接点击
                        executor.executeScript("arguments[0].click();", batchBtn);
                        log.info("使用JS点击投递按钮");
                        success = true;
                    } catch (Exception e) {
                        // 方法3：尝试Actions点击
                        try {
                            ACTIONS.moveToElement(batchBtn).click().build().perform();
                            log.info("使用Actions点击投递按钮");
                            success = true;
                        } catch (Exception ae) {
                            // 方法4：使用JS触发mousedown和mouseup事件
                            try {
                                executor.executeScript("var ev = new MouseEvent('mousedown', {bubbles: true, cancelable: true}); arguments[0].dispatchEvent(ev);", batchBtn);
                                SeleniumUtil.sleep(100);
                                executor.executeScript("var ev = new MouseEvent('mouseup', {bubbles: true, cancelable: true}); arguments[0].dispatchEvent(ev);", batchBtn);
                                log.info("使用JS触发mousedown/mouseup事件点击投递按钮");
                                success = true;
                            } catch (Exception je) {
                                log.error("所有点击方法都失败: {}", je.getMessage());
                            }
                        }
                    }
                } else {
                    log.error("未找到批量投递按钮");
                    // 尝试查找页面上所有按钮并打印，以便调试
                    try {
                        List<WebElement> allButtons = CHROME_DRIVER.findElements(By.tagName("button"));
                        log.info("页面上找到 {} 个按钮", allButtons.size());
                        for (int i = 0; i < Math.min(allButtons.size(), 5); i++) {
                            WebElement btn = allButtons.get(i);
                            log.info("按钮 {}: text={}, class={}", i, btn.getText(), btn.getAttribute("class"));
                        }
                    } catch (Exception e) {
                        log.warn("获取页面按钮信息失败: {}", e.getMessage());
                    }
                }
                
                if (!success) {
                    SeleniumUtil.sleep(3);
                    // 刷新页面重试
                    if (retryCount % 3 == 0) {
                        log.info("多次尝试失败，刷新页面后重试");
                        CHROME_DRIVER.navigate().refresh();
                        SeleniumUtil.sleep(5);
                    }
                }
            } catch (Exception e) {
                log.error("批量投递按钮交互异常，重试... {}", e.getMessage());
                SeleniumUtil.sleep(3);
            }
        }
        
        if (!success) {
            log.error("达到最大重试次数，批量投递失败");
        }

        try {
            SeleniumUtil.sleep(3);
            String text = CHROME_DRIVER.findElement(By.xpath("//div[@class='successContent']")).getText();
            if (text.contains("快来扫码下载~")) {
                //关闭弹窗
                CHROME_DRIVER.findElement(By.cssSelector("[class*='van-icon van-icon-cross van-popup__close-icon van-popup__close-icon--top-right']")).click();
            }
        } catch (Exception ignored) {
            log.info("未找到投递成功弹窗！可能为单独投递申请弹窗！");
        }
        String particularly = null;
        try {
            particularly = CHROME_DRIVER.findElement(By.xpath("//div[@class='el-dialog__body']/span")).getText();
        } catch (Exception ignored) {
        }
        if (particularly != null && particularly.contains("需要到企业招聘平台单独申请")) {
            //关闭弹窗
            CHROME_DRIVER.findElement(By.cssSelector("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div:nth-child(2) > div:nth-child(2) > div > div.el-dialog__header > button > i")).click();
            log.info("关闭单独投递申请弹窗成功！");
        }
    }

    private static void findAnomaly() {
        try {
            String verify = CHROME_DRIVER.findElement(By.xpath("//p[@class='waf-nc-title']")).getText();
            if (verify.contains("验证")) {
                //关闭弹窗
                log.error("出现访问验证了！程序退出...");
                printResult(); // printResult已经包含了关闭浏览器的逻辑
            }
        } catch (Exception ignored) {
            log.info("未出现访问验证，继续运行...");
        }
    }

    private static void scanLogin() {
        log.info("等待扫码登陆..");
        CHROME_DRIVER.get(loginUrl);
        
        // 记录开始时间，用于判断20分钟超时（用户要求延长）
        long startTime = System.currentTimeMillis();
        final long TIMEOUT = 20 * 60 * 1000; // 20分钟
        
        // 等待登录成功元素出现，但增加超时控制
        while (true) {
            try {
                // 检查是否登录成功
                WebElement resumeElement = CHROME_DRIVER.findElement(By.xpath("//a[contains(text(), '在线简历')]"));
                if (resumeElement != null && resumeElement.isDisplayed()) {
                    log.info("扫码登录成功！");
                    SeleniumUtil.saveCookie("job51", cookiePath);
                    break;
                }
            } catch (Exception ignored) {
                // 元素未找到，继续等待
            }
            
            // 检查是否超时
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= TIMEOUT) {
                log.error("登录超时，20分钟内未完成扫码登录，程序将退出。");
                return;
            }
            
            // 每2秒检查一次
            SeleniumUtil.sleep(2);
        }
    }

}
