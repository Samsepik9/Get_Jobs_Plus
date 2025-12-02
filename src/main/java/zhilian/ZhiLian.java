package zhilian;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Job;
import utils.JobUtils;
import utils.SeleniumUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static utils.Bot.sendMessageByTime;
import static utils.Constant.*;
import static utils.JobUtils.formatDuration;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
public class ZhiLian {
    static {
        // 在类加载时就设置日志文件名，确保Logger初始化时能获取到正确的属性
        System.setProperty("log.name", "zhilian");
    }
    
    private static final Logger log = LoggerFactory.getLogger(ZhiLian.class);
    static String loginUrl = "https://passport.zhaopin.com/login";
    static String homeUrl = "https://sou.zhaopin.com/?";
    static boolean isLimit = false;
    static int maxPage = 500;
    static ZhilianConfig config = ZhilianConfig.init();
    static List<Job> resultList = new ArrayList<>();
    static Date startDate;
    // 平台特定的浏览器实例
    private static ChromeDriver driver;
    private static WebDriverWait wait;
    private static Actions actions;

    public static void main(String[] args) {
        log.info("智联招聘投递任务开始");
        try {
            // 使用平台特定的浏览器实例初始化
            SeleniumUtil.initDriver("zhilian");
            driver = SeleniumUtil.getChromeDriverInstance("zhilian");
            if (driver == null) {
                log.error("获取智联招聘浏览器实例失败");
                return;
            }
            wait = SeleniumUtil.fetchWait("zhilian", 60);
            actions = SeleniumUtil.fetchActions("zhilian");
            startDate = new Date();
            login();
            config.getKeywords().forEach(keyword -> {
                if (isLimit) {
                    return;
                }
                driver.get(getSearchUrl(keyword, 1));
                submitJobs(keyword);
            });
            log.info(resultList.isEmpty() ? "未投递新的岗位..." : "新投递公司如下:\n{}", resultList.stream().map(Object::toString).collect(Collectors.joining("\n")));
            printResult();
        } catch (Exception e) {
            log.error("智联招聘投递任务发生异常", e);
            printResult();
        } finally {
            // 关闭该平台的浏览器实例
            SeleniumUtil.closePlatform("zhilian");
        }
    }

    private static void printResult() {
        String message = String.format("\n智联招聘投递完成，共投递%d个岗位，用时%s", resultList.size(), formatDuration(startDate, new Date()));
        log.info(message);
        try {
            sendMessageByTime(message);
        } catch (Exception e) {
            log.error("发送消息失败: {}", e.getMessage());
        }
        resultList.clear();
        
        // 确保所有日志都被刷新到文件
        try {
            Thread.sleep(1000); // 等待1秒确保日志写入完成
            // 强制刷新日志 - 使用正确的方法
            ch.qos.logback.classic.LoggerContext loggerContext = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
            loggerContext.stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String getSearchUrl(String keyword, int page) {
        return homeUrl +
                JobUtils.appendParam("jl", config.getCityCode()) +
                JobUtils.appendParam("kw", keyword) +
                JobUtils.appendParam("sl", config.getSalary()) +
                "&p=" + page;
    }

    private static void submitJobs(String keyword) {
        if (isLimit) {
            return;
        }
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'joblist-box__item')]")));
        setMaxPages();
        for (int i = 1; i <= maxPage; i++) {
            if (i != 1) {
                driver.get(getSearchUrl(keyword, i));
            }
            log.info("开始投递【{}】关键词，第【{}】页...", keyword, i);
            // 等待岗位出现
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='positionlist']")));
            } catch (Exception ignore) {
                driver.navigate().refresh();
                SeleniumUtil.sleep(1);
            }
            // 全选（增强选择器 + 回退为逐条勾选前 N 个）
            boolean selectedAny = false;
            try {
                // 多种候选选择器
                By[] allSelectors = new By[] {
                        By.xpath("//i[@class='betch__checkall__checkbox']"),
                        By.cssSelector(".betch__checkall__checkbox, .checkall .checkbox, .select-all input, .select-all .checkbox"),
                        By.xpath("//div[contains(@class,'checkall') or contains(@class,'select-all')]//input | //div[contains(@class,'checkall') or contains(@class,'select-all')]//i")
                };
                WebElement allSelect = null;
                for (By by : allSelectors) {
                    try {
                        allSelect = wait.until(ExpectedConditions.presenceOfElementLocated(by));
                        if (allSelect != null) break;
                    } catch (Exception ignore) {}
                }
                if (allSelect != null) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", allSelect);
                    wait.until(ExpectedConditions.elementToBeClickable(allSelect));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", allSelect);
                    selectedAny = true;
                }
            } catch (Exception ignore) {}

            if (!selectedAny) {
                // 回退：逐条勾选前 N 个岗位（避免页面结构变更导致的全选失败）
                int maxPick = 10; // 可调整
                List<WebElement> checkboxes = driver.findElements(By.xpath("//div[contains(@class,'joblist-box__item')]//input[@type='checkbox' or contains(@class,'checkbox')] | //div[contains(@class,'joblist-box__item')]//i[contains(@class,'checkbox')]"));
                int picked = 0;
                for (WebElement cb : checkboxes) {
                    try {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", cb);
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", cb);
                        picked++;
                        if (picked >= maxPick) break;
                    } catch (Exception ignore) {}
                }
                selectedAny = picked > 0;
                if (!selectedAny) {
                    log.info("未找到可选择的岗位复选框，跳过本页");
                    continue;
                }
            }

            // 投递（增强选择器 + JS 点击）
            WebElement submit = null;
            By[] submitSelectors = new By[] {
                    By.xpath("//button[@class='betch__button']"),
                    By.cssSelector(".betch__button, button.apply, button.submit, button[class*='batch']"),
                    By.xpath("//button[contains(text(),'投递') or contains(text(),'申请') or contains(text(),'提交')]")
            };
            for (By by : submitSelectors) {
                try {
                    submit = wait.until(ExpectedConditions.presenceOfElementLocated(by));
                    if (submit != null) break;
                } catch (Exception ignore) {}
            }
            if (submit == null) {
                log.info("未找到投递按钮，跳过本页");
                continue;
            }
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", submit);
            wait.until(ExpectedConditions.elementToBeClickable(submit));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", submit);
            if (checkIsLimit()) {
                break;
            }
            SeleniumUtil.sleep(1);
            // 切换到新的标签页
            ArrayList<String> tabs = new ArrayList<>(driver.getWindowHandles());
            driver.switchTo().window(tabs.get(tabs.size() - 1));
            
            // 尝试发送打招呼消息
            try {
                // 查找立即沟通或发送消息按钮
                List<WebElement> contactButtons = driver.findElements(By.xpath(".//*[contains(text(), '立即沟通') or contains(text(), '发送消息') or contains(@class, 'contact') or contains(@class, 'message')]"));
                if (!contactButtons.isEmpty()) {
                    WebElement contactButton = contactButtons.get(0);
                    contactButton.click();
                    SeleniumUtil.sleep(2);
                    
                    // 查找消息输入框
                    try {
                        WebElement messageInput = driver.findElement(By.xpath(".//*[@class='message-input' or @class='chat-input' or @placeholder='请输入消息内容' or @type='text']"));
                        if (messageInput != null) {
                            String greeting = "您好，我对贵公司的岗位很感兴趣，期待能进一步沟通！";
                            messageInput.sendKeys(greeting);
                            
                            // 查找发送按钮并点击
                            WebElement sendButton = driver.findElement(By.xpath(".//*[contains(text(), '发送') or @class='send-btn']"));
                            if (sendButton != null) {
                                sendButton.click();
                                log.info("成功发送打招呼消息");
                                SeleniumUtil.sleep(1);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("未找到消息输入框或发送按钮: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("发送打招呼消息失败: {}", e.getMessage());
            }
            
            //关闭弹框
            try {
                WebElement result = driver.findElement(By.xpath("//div[@class='deliver-dialog']"));
                if (result.getText().contains("申请成功")) {
                    log.info("岗位申请成功！");
                }
            } catch (Exception e) {
                log.error("关闭投递弹框失败...");
            }
            try {
                WebElement close = driver.findElement(By.xpath("//img[@title='close-icon']"));
                close.click();
            } catch (Exception e) {
                if (checkIsLimit()) {
                    break;
                }
            }
            try {
                // 投递相似职位
                WebElement checkButton = driver.findElement(By.xpath("//div[contains(@class, 'applied-select-all')]//input"));
                if (!checkButton.isSelected()) {
                    checkButton.click();
                }
                List<WebElement> jobs = driver.findElements(By.xpath("//div[@class='recommend-job']"));
                WebElement post = driver.findElement(By.xpath("//div[contains(@class, 'applied-select-all')]//button"));
                post.click();
                printRecommendJobs(jobs);
                log.info("相似职位投递成功！");
            } catch (NoSuchElementException e) {
                log.error("没有匹配到相似职位...");
            } catch (Exception e) {
                log.error("相似职位投递异常！！！");
            }
            // 投完了关闭当前窗口并切换至第一个窗口
            driver.close();
            driver.switchTo().window(tabs.get(0));
        }
    }

    private static boolean checkIsLimit() {
        try {
            SeleniumUtil.sleepByMilliSeconds(500);
            WebElement result = driver.findElement(By.xpath("//div[@class='a-job-apply-workflow']"));
            if (result.getText().contains("达到上限")) {
                log.info("今日投递已达上限！");
                isLimit = true;
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static void setMaxPages() {
        try {
            // 到底部
            actions.keyDown(Keys.CONTROL).sendKeys(Keys.END).keyUp(Keys.CONTROL).perform();
            WebElement inputElement = driver.findElement(By.className("soupager__pagebox__goinp"));
            inputElement.clear();
            inputElement.sendKeys("99999");
            //使用 JavaScript 获取输入元素的当前值
            JavascriptExecutor js = driver;
            String modifiedValue = (String) js.executeScript("return arguments[0].value;", inputElement);
            maxPage = Integer.parseInt(modifiedValue);
            log.info("设置最大页数：{}", maxPage);
            WebElement home = driver.findElement(By.xpath("//li[@class='listsort__item']"));
            actions.moveToElement(home).perform();
        } catch (Exception ignore) {
            StackTraceElement element = Thread.currentThread().getStackTrace()[1];
            log.info("setMaxPages@设置最大页数异常！({}:{})", element.getFileName(), element.getLineNumber());
            log.info("设置默认最大页数50，如有需要请自行调整...");
            maxPage = 50;
        }
    }

    private static void printRecommendJobs(List<WebElement> jobs) {
        jobs.forEach(j -> {
            String jobName = j.findElement(By.xpath(".//*[contains(@class, 'recommend-job__position')]")).getText();
            String salary = j.findElement(By.xpath(".//span[@class='recommend-job__demand__salary']")).getText();
            String years = j.findElement(By.xpath(".//span[@class='recommend-job__demand__experience']")).getText().replaceAll("\n", " ");
            String education = j.findElement(By.xpath(".//span[@class='recommend-job__demand__educational']")).getText().replaceAll("\n", " ");
            String companyName = j.findElement(By.xpath(".//*[contains(@class, 'recommend-job__cname')]")).getText();
            String companyTag = j.findElement(By.xpath(".//*[contains(@class, 'recommend-job__demand__cinfo')]")).getText().replaceAll("\n", " ");
            Job job = new Job();
            job.setJobName(jobName);
            job.setSalary(salary);
            job.setCompanyTag(companyTag);
            job.setCompanyName(companyName);
            job.setJobInfo(years + "·" + education);
            log.info("投递【{}】公司【{}】岗位，薪资【{}】，要求【{}·{}】，规模【{}】", companyName, jobName, salary, years, education, companyTag);
            resultList.add(job);
        });
    }

    private static void login() {
        driver.get(loginUrl);
        if (SeleniumUtil.isCookieValid("./src/main/java/zhilian/cookie.json")) {
            SeleniumUtil.loadCookie("./src/main/java/zhilian/cookie.json", "zhilian");
            driver.navigate().refresh();
            SeleniumUtil.sleep(1);
        }
        if (isLoginRequired()) {
            scanLogin();
        }
    }

    private static void scanLogin() {
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                // 尝试多种可能的扫码按钮定位方式
                WebElement button = null;
                try {
                    button = driver.findElement(By.xpath("//div[@class='zppp-panel-normal-bar__img']"));
                } catch (Exception e1) {
                    try {
                        button = driver.findElement(By.xpath("//button[contains(text(), '扫码登录') or contains(@class, 'scan-btn')]"));
                    } catch (Exception e2) {
                        log.warn("未找到默认扫码按钮，尝试其他定位方式...");
                        // 输出页面上的按钮信息帮助调试
                        List<WebElement> buttons = driver.findElements(By.tagName("button"));
                        for (int i = 0; i < Math.min(5, buttons.size()); i++) {
                            try {
                                log.debug("按钮 {} 文本: {}, 类名: {}", i, buttons.get(i).getText(), buttons.get(i).getAttribute("class"));
                            } catch (Exception ignore) {}
                        }
                        throw new Exception("未找到扫码登录按钮");
                    }
                }
                
                button.click();
                log.info("等待扫码登录中... ({}秒后超时，可刷新页面重新扫码)", 60 * (retryCount + 1));
                
                // 使用自定义等待逻辑，避免直接抛出异常
                long endTime = System.currentTimeMillis() + 60000L * (retryCount + 1); // 动态增加等待时间
                boolean loginSuccess = false;
                
                while (System.currentTimeMillis() < endTime) {
                    try {
                        if (isLoginRequired()) {
                            SeleniumUtil.sleep(2);
                            continue;
                        }
                        loginSuccess = true;
                        break;
                    } catch (Exception e) {
                        SeleniumUtil.sleep(2);
                    }
                }
                
                if (loginSuccess) {
                    log.info("扫码登录成功！");
                    try {
                        SeleniumUtil.saveCookie("./src/main/java/zhilian/cookie.json");
                        log.info("Cookie已保存");
                    } catch (Exception e) {
                        log.warn("保存Cookie失败，但不影响登录状态: {}", e.getMessage());
                    }
                    return;
                } else {
                    log.warn("扫码登录超时，正在重试 ({}/{})...", retryCount + 1, maxRetries);
                    SeleniumUtil.sleep(3);
                    CHROME_DRIVER.navigate().refresh();
                    SeleniumUtil.sleep(3);
                }
                
            } catch (Exception e) {
                    log.error("扫码登录过程中发生异常: {}", e.getMessage());
                    log.debug("异常详情:", e);
                SeleniumUtil.sleep(3);
                driver.navigate().refresh();
                SeleniumUtil.sleep(3);
            }
            
            retryCount++;
        }
        
        log.error("登录失败，已达到最大重试次数");
        // 不再使用System.exit，让调用者决定如何处理
    }

    private static boolean isLoginRequired() {
        return !driver.getCurrentUrl().contains("i.zhaopin.com");
    }
}
