package liepin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import lombok.SneakyThrows;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import utils.JobUtils;
import utils.PlaywrightUtil;
import utils.SeleniumUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.lang.Thread;

import static liepin.Locators.*;
import static utils.Bot.sendMessageByTime;
import static utils.JobUtils.formatDuration;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
public class Liepin {
    private static final Logger log = LoggerFactory.getLogger(Liepin.class);
    static String homeUrl = "https://www.liepin.com/";
    static String cookiePath = "./src/main/java/liepin/cookie.json";
    static String dataPath = "src/main/java/liepin/data.json";
    static Set<String> blackCompanies = new HashSet<>();
    static Set<String> blackRecruiters = new HashSet<>();
    static Set<String> blackJobs = new HashSet<>();
    static int maxPage = 50;
    static List<String> resultList = new ArrayList<>();
    static String baseUrl = "https://www.liepin.com/zhaopin/?";
    static LiepinConfig config = LiepinConfig.init();
    static Date startDate;
    
    static {
        // 在类加载时就设置日志文件名，确保Logger初始化时能获取到正确的属性
        System.setProperty("log.name", "liepin");
        
        try {
            // 检查dataPath文件是否存在，不存在则创建
            File dataFile = new File(dataPath);
            if (!dataFile.exists()) {
                // 确保父目录存在
                if (!dataFile.getParentFile().exists()) {
                    dataFile.getParentFile().mkdirs();
                }
                // 创建文件并写入初始JSON结构
                Map<String, Set<String>> initialData = new HashMap<>();
                initialData.put("blackCompanies", new HashSet<>());
                initialData.put("blackRecruiters", new HashSet<>());
                initialData.put("blackJobs", new HashSet<>());
                String initialJson = customJsonFormat(initialData);
                Files.write(Paths.get(dataPath), initialJson.getBytes());
                log.info("创建数据文件: {}", dataPath);
            }

            // 检查cookiePath文件是否存在，不存在则创建
            File cookieFile = new File(cookiePath);
            if (!cookieFile.exists()) {
                // 确保父目录存在
                if (!cookieFile.getParentFile().exists()) {
                    cookieFile.getParentFile().mkdirs();
                }
                // 创建空的cookie文件
                Files.write(Paths.get(cookiePath), "[]".getBytes());
                log.info("创建cookie文件: {}", cookiePath);
            }
        } catch (IOException e) {
            log.error("创建文件时发生异常: {}", e.getMessage());
        }
        
        // 加载黑名单数据
        loadData(dataPath);
    }

    /**
     * 保存页面源码到日志和文件，用于调试
     */
    private static void savePageSource(Page page, String context) {
        try {
            String pageSource = page.content();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            
            // 保存完整源码到文件
            Path sourceDir = Paths.get("./target/logs/page_sources");
            Files.createDirectories(sourceDir);
            
            String fileName = String.format("liepin_page_%s_%s.html", context.replaceAll("[^a-zA-Z0-9]", "_"), timestamp);
            Path sourceFile = sourceDir.resolve(fileName);
            Files.write(sourceFile, pageSource.getBytes("UTF-8"));
            
            log.info("完整页面源码已保存到文件: {}", sourceFile.toAbsolutePath());
            
        } catch (IOException e) {
            log.error("保存页面源码失败: {}", e.getMessage());
        }
    }



    public static void main(String[] args) {
        // 确保日志配置正确加载
        log.info("猎聘网自动投递程序启动");
        
        try {
            // 初始化 Playwright（使用平台特定的浏览器实例）
            PlaywrightUtil.init("liepin");
            startDate = new Date();
            
            // 登录猎聘网
            if (!isLoginRequired()) {
                log.info("已登录，准备投递...");
            } else {
                login();
                // 再次检查登录状态
                if (isLoginRequired()) {
                    log.error("登录失败，程序终止");
                    PlaywrightUtil.close();
                    return;
                }
            }
            
            // 获取关键词并进行投递
            List<String> keywords = config.getKeywords();
            log.info("将投递的关键词列表: {}", keywords);
            
            for (String keyword : keywords) {
                try {
                    // 由于无法直接检查浏览器状态，我们依赖异常处理
                    submit(keyword);
                } catch (Exception e) {
                    log.error("处理关键词 [{}] 时发生异常: {}", keyword, e.getMessage());
                    log.debug("异常详情:", e);
                    // 继续处理下一个关键词
                }
            }
            
            printResult();
        } catch (Exception e) {
            log.error("程序运行发生异常: {}", e.getMessage());
            log.debug("异常详情:", e);
        } finally {
            // 确保资源正确关闭
            try {
                PlaywrightUtil.close();
                log.info("浏览器资源已释放");
            } catch (Exception e) {
                log.warn("关闭浏览器资源时发生错误: {}", e.getMessage());
            }
        }
    }

    private static void printResult() {
        try {
            String message = String.format("\n猎聘投递完成，共投递%d个岗位，用时%s", resultList.size(), formatDuration(startDate, new Date()));
            log.info(message);
            log.info("黑名单公司数量: {}", blackCompanies.size());
            log.info("黑名单岗位数量: {}", blackJobs.size());
            log.info("黑名单招聘者数量: {}", blackRecruiters.size());
            
            // 安全调用sendMessageByTime，添加异常处理
            try {
                sendMessageByTime(message);
            } catch (Exception e) {
                log.error("发送消息失败: {}", e.getMessage());
            }
            
            resultList.clear();
            
            // 保存黑名单数据
            saveData(dataPath);
            
            // 移除重复的PlaywrightUtil.close()调用，因为main方法的finally块会处理
            
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
    
    private static void saveData(String path) {
        try {
            Map<String, Set<String>> data = new HashMap<>();
            data.put("blackCompanies", blackCompanies);
            data.put("blackRecruiters", blackRecruiters);
            data.put("blackJobs", blackJobs);
            String json = customJsonFormat(data);
            Files.write(Paths.get(path), json.getBytes());
            log.info("黑名单数据已保存到: {}", path);
        } catch (IOException e) {
            log.error("保存【{}】数据失败！", path);
        }
    }
    
    private static String customJsonFormat(Map<String, Set<String>> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (Map.Entry<String, Set<String>> entry : data.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": [\n");
            sb.append(entry.getValue().stream().map(s -> "        \"" + s + "\"").collect(Collectors.joining(",\n")));
            
            sb.append("\n    ],\n");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append("\n}");
        return sb.toString();
    }
    
    private static void loadData(String path) {
        try {
            String json = new String(Files.readAllBytes(Paths.get(path)));
            parseJson(json);
        } catch (IOException e) {
            log.error("读取【{}】数据失败！", path);
        }
    }
    
    private static void parseJson(String json) {
        JSONObject jsonObject = new JSONObject(json);
        blackCompanies = jsonObject.getJSONArray("blackCompanies").toList().stream().map(Object::toString)
                .collect(Collectors.toSet());
        blackRecruiters = jsonObject.getJSONArray("blackRecruiters").toList().stream().map(Object::toString)
                .collect(Collectors.toSet());
        blackJobs = jsonObject.getJSONArray("blackJobs").toList().stream().map(Object::toString)
                .collect(Collectors.toSet());
        log.info("已加载黑名单数据 - 公司: {}, 岗位: {}, 招聘者: {}", 
                 blackCompanies.size(), blackJobs.size(), blackRecruiters.size());
    }


    @SneakyThrows
    private static void submit(String keyword) {
        try {
            Page page = PlaywrightUtil.getPageObject("liepin");
            
            if (page == null) {
                log.error("无法获取页面对象，跳过关键词: {}", keyword);
                return;
            }
            
            // 重试导航到搜索页面
            int navigateRetries = 3;
            boolean navigateSuccess = false;
            
            for (int retry = 0; retry < navigateRetries; retry++) {
                try {
                    
                    log.info("尝试导航到搜索页面，关键词: {}，重试次数: {}/{}", keyword, retry + 1, navigateRetries);
                    String searchUrl = getSearchUrl(keyword);
                    log.info("构建的搜索URL: {}", searchUrl);
                    page.navigate(searchUrl);
                    
                    // 等待页面加载完成
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    log.debug("页面导航完成，URL: {}", page.url());
                    navigateSuccess = true;
                    break;
                } catch (Exception e) {
                    log.error("导航失败: {}", e.getMessage());
                    if (retry < navigateRetries - 1) {
                        log.info("{}秒后重试...", (retry + 1) * 2);
                        try {
                            Thread.sleep((retry + 1) * 2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
            
            if (!navigateSuccess) {
                log.error("多次尝试导航失败，跳过关键词: {}", keyword);
                return;
            }
            
            // 尝试获取最大页数，但不作为必需步骤
            try {
                // 尝试多种可能的分页选择器
                String[] paginationSelectors = {
                    PAGINATION_BOX,
                    "//div[contains(@class, 'pagination')]",
                    "//div[contains(@class, 'page')]",
                    "//div[contains(@class, 'pages')]"
                };
                
                for (String selector : paginationSelectors) {
                    try {
                        Locator paginationBox = page.locator(selector);
                        if (paginationBox.count() > 0) {
                            log.debug("找到分页元素: {}", selector);
                            Locator lis = paginationBox.locator("li");
                            if (lis.count() > 0) {
                                setMaxPage(lis);
                                log.debug("通过分页元素设置最大页面数: {}", maxPage);
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception e) {
                log.warn("无法获取分页信息，使用默认最大页面数: {}", maxPage);
            }
            
            // 限制最大页面数以避免无限循环
            int maxPagesToProcess = Math.min(maxPage, 5); // 最多处理5页
            log.info("开始投递关键词: {}, 计划处理页面数: {}", keyword, maxPagesToProcess);
            
            for (int i = 0; i < maxPagesToProcess; i++) {
                try {
                    // 尝试关闭订阅弹窗或其他干扰元素
                    try {
                        // 尝试多种可能的关闭按钮
                        String[] closeButtonSelectors = {
                            SUBSCRIBE_CLOSE_BTN,
                            "//button[contains(@class, 'close')]",
                            "//i[contains(@class, 'close')]",
                            "//span[contains(@class, 'close')]"
                        };
                        
                        for (String selector : closeButtonSelectors) {
                            try {
                                Locator closeBtn = page.locator(selector);
                                if (closeBtn.count() > 0) {
                                    log.debug("找到并点击关闭按钮: {}", selector);
                                    closeBtn.click();
                                    Thread.sleep(500);
                                    break;
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    
                    // 尝试多种可能的岗位卡片选择器
                    boolean jobsLoaded = false;
                    
                    // 尝试多种可能的岗位卡片选择器
                    String[] jobCardSelectors = {
                        JOB_CARDS,
                        "//div[contains(@class, 'job-card')]",
                        "//div[contains(@class, 'job-item')]",
                        "//div[contains(@class, 'job-post')]",
                        "//li[contains(@class, 'job-card')]",
                        "//div[contains(@data-qa, 'job-card')]"
                    };
                    
                    // 尝试每个选择器，直到找到匹配的元素
                    for (int retry = 0; retry < 3; retry++) {
                        try {
                            log.debug("等待岗位卡片加载，页码: {}, 重试: {}", i + 1, retry + 1);
                            
                            // 首先等待页面完全加载
                            page.waitForLoadState(LoadState.NETWORKIDLE);
                            
                            boolean foundJobCards = false;
                            for (String selector : jobCardSelectors) {
                                try {
                                    log.debug("尝试岗位卡片选择器: {}", selector);
                                    Locator locator = page.locator(selector);
                                    if (locator.count() > 0) {
                                        log.info("找到 {} 个岗位卡片，选择器: {}", locator.count(), selector);
                                        jobsLoaded = true;
                                        foundJobCards = true;
                                        break;
                                    }
                                } catch (Exception e) {
                                    log.debug("尝试选择器 {} 失败: {}", selector, e.getMessage());
                                }
                            }
                            
                            if (foundJobCards) {
                                break;
                            } else {
                                log.warn("未找到岗位卡片，{}秒后重试", (retry + 1) * 3);
                                try {
                                    Thread.sleep((retry + 1) * 3000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                                // 尝试刷新页面
                                page.reload();
                            }
                        } catch (Exception e) {
                            log.warn("等待岗位卡片时发生错误: {}，{}秒后重试", e.getMessage(), (retry + 1) * 3);
                            try {
                                Thread.sleep((retry + 1) * 3000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            page.reload();
                        }
                    }
                    
                    if (jobsLoaded) {
                        log.info("正在投递【{}】第【{}】页...", keyword, i + 1);
                        try {
                            submitJob();
                            log.info("已投递第【{}】页所有的岗位...\n", i + 1);
                        } catch (Exception e) {
                            log.error("投递岗位时出错: {}", e.getMessage());
                            // 继续处理，不中断整个流程
                        }
                    } else {
                        log.warn("第【{}】页未能加载岗位卡片，跳过", i + 1);
                    }
                    
                    // 查找下一页按钮 - 增加健壮性
                    try {
                        // 尝试多种可能的下一页选择器
                        String[] nextPageSelectors = {
                            NEXT_PAGE,
                            "li[title='下一页']",
                            "//li[contains(text(), '下一页')]",
                            "//a[contains(text(), '下一页')]"
                        };
                        
                        boolean foundNextPage = false;
                        for (String selector : nextPageSelectors) {
                            try {
                                Locator paginationBox = page.locator(PAGINATION_BOX);
                                Locator nextPage = paginationBox.locator(selector);
                                if (nextPage.count() > 0 && nextPage.getAttribute("disabled") == null) {
                                    log.debug("找到下一页按钮: {}", selector);
                                    nextPage.click();
                                    Thread.sleep(2000); // 给足够时间加载下一页
                                    foundNextPage = true;
                                    break;
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        
                        if (!foundNextPage) {
                            log.info("未找到可用的下一页按钮，结束翻页");
                            break;
                        }
                    } catch (Exception e) {
                        log.error("翻页时出错: {}", e.getMessage());
                        break;
                    }
                    
                } catch (Exception e) {
                    log.error("处理第【{}】页时发生异常: {}", i + 1, e.getMessage());
                    // 继续处理下一页，不中断整个关键词的处理
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            log.info("【{}】关键词投递完成！", keyword);
        } catch (Exception e) {
            log.error("处理关键词【{}】时发生异常: {}", keyword, e.getMessage());
            // 即使发生异常也继续执行，不中断程序
        }
    }

    private static String getSearchUrl(String keyword) {
        // 优化城市筛选：确保城市参数正确添加到URL中，实现服务器端过滤
        // 使用URL编码确保关键词安全
        String cityCode = config.getCityCode();
        String encodedKeyword;
        try {
            encodedKeyword = java.net.URLEncoder.encode(keyword, "UTF-8");
        } catch (Exception e) {
            encodedKeyword = keyword; // 编码失败时使用原始关键词
        }
        
        // 构建完整的搜索URL，确保城市参数正确设置
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append("city=").append(cityCode);
        urlBuilder.append("&dq=").append(cityCode);
        urlBuilder.append("&key=").append(encodedKeyword);
        urlBuilder.append("&currentPage=0");
        urlBuilder.append(JobUtils.appendParam("salary", config.getSalary()));
        urlBuilder.append(JobUtils.appendParam("pubTime", config.getPubTime()));
        
        // 添加其他可能有用的参数以获取更精确的结果
        urlBuilder.append("&dqs=").append(cityCode); // 额外的城市筛选参数
        
        String url = urlBuilder.toString();
        log.info("搜索URL: {}, 城市代码: {}", url, cityCode);
        return url;
    }


    private static void setMaxPage(Locator lis) {
        try {
            int count = lis.count();
            if (count >= 2) {
                String pageText = lis.nth(count - 2).textContent();
                int page = Integer.parseInt(pageText);
                if (page > 1) {
                    maxPage = page;
                }
            }
        } catch (Exception ignored) {
        }
    }
    
    /**
     * 根据城市代码获取城市名称
     * @param cityCode 城市代码
     * @return 城市名称
     */
    private static String getCityNameFromCode(String cityCode) {
        for (LiepinEnum.CityCode city : LiepinEnum.CityCode.values()) {
            if (city.getCode().equals(cityCode)) {
                return city.getName();
            }
        }
        return "未知";
    }

    private static void submitJob() {
        Page page = PlaywrightUtil.getPageObject("liepin");
        
        if (page == null) {
            log.error("无法获取页面对象，跳过岗位投递");
            return;
        }
        
        // 等待页面完全加载
        // try {
        //     page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
        // } catch (Exception e) {
        //     log.warn("等待页面网络空闲超时，继续执行: {}", e.getMessage());
        // }
        
        // 获取hr数量
        Locator jobCards = page.locator(JOB_CARDS);
        
        // 等待岗位卡片加载完成
        // try {
        //     jobCards.first().waitFor(new Locator.WaitForOptions().setTimeout(10000));
        // } catch (Exception e) {
        //     log.warn("等待岗位卡片加载超时: {}", e.getMessage());
        // }
        
        int count = jobCards.count();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {

            Locator jobTitleElements = page.locator(JOB_TITLE);
            Locator companyNameElements = page.locator(COMPANY_NAME);
            Locator salaryElements = page.locator(JOB_SALARY);
            
            if (i >= jobTitleElements.count() || i >= companyNameElements.count() || i >= salaryElements.count()) {
                continue;
            }
            
            String jobName = jobTitleElements.nth(i).textContent().replaceAll("\n", " ").replaceAll("【 ", "[").replaceAll(" 】", "]");
            String companyName = companyNameElements.nth(i).textContent().replaceAll("\n", " ");
            String salary = salaryElements.nth(i).textContent().replaceAll("\n", " ");
            String recruiterName = null;
            
            // 检查是否在黑名单中
            if (blackJobs.stream().anyMatch(jobName::contains)) {
                log.debug("过滤黑名单岗位: {}", jobName);
                continue;
            }
            if (blackCompanies.stream().anyMatch(companyName::contains)) {
                log.debug("过滤黑名单公司: {}", companyName);
                continue;
            }
            
            // 服务器端已经过滤了城市，但为了安全起见，保留客户端过滤作为双重保障
            // 从配置中获取目标城市名称
            String targetCityName = getCityNameFromCode(config.getCityCode());
            
            // 优化：只有在确实需要时才进行客户端过滤
            // 因为服务器端已经通过URL参数过滤了城市，但仍可能有少量不匹配的结果
            boolean isTargetCityJob = jobName.contains("【" + targetCityName + "") || 
                                     jobName.contains("[" + targetCityName + "-") ||
                                     jobName.contains("【" + targetCityName + "-");
            
            if (!isTargetCityJob) {
                // 由于服务器端已过滤，只记录不匹配的情况，不再详细记录
                log.debug("客户端过滤：跳过非{}岗位: {}", targetCityName, jobName);
                continue;
            }
            
            // 尝试获取招聘者名称并检查是否在黑名单中
            try {
                // 尝试多种可能的招聘者选择器
                String[] recruiterSelectors = {
                    ".job-card__info .recruiter-name",
                    ".job-info__tag-list .recruiter-name",
                    "//span[contains(@class, 'recruiter-name')]"
                };
                
                for (String selector : recruiterSelectors) {
                    try {
                        Locator recruiterElem = page.locator(selector);
                        if (recruiterElem.count() > 0) {
                            recruiterName = recruiterElem.textContent().trim();
                            break;
                        }
                    } catch (Exception ignored) {}
                }
                
                if (recruiterName != null && !recruiterName.isEmpty()) {
                    if (blackRecruiters.stream().anyMatch(recruiterName::contains)) {
                        log.debug("过滤黑名单招聘者: {}", recruiterName);
                        continue;
                    }
                }
            } catch (Exception e) {
                log.debug("获取招聘者信息失败: {}", e.getMessage());
            }
            
            log.info("处理{}岗位: {}", targetCityName, jobName);
            
            try {
                // 获取当前岗位卡片
                Locator currentJobCard = page.locator(JOB_CARDS).nth(i);
                
                // 使用JavaScript滚动到卡片位置，更稳定
                try {
                    // 先滚动到卡片位置
                    page.evaluate("(element) => element.scrollIntoView({behavior: 'instant', block: 'center'})", currentJobCard.elementHandle());
                    // PlaywrightUtil.sleep(1); // 等待滚动完成
                    
                    // 再次确保元素在视窗中
                    page.evaluate("(element) => { const rect = element.getBoundingClientRect(); if (rect.top < 0 || rect.bottom > window.innerHeight) { element.scrollIntoView({behavior: 'instant', block: 'center'}); } }", currentJobCard.elementHandle());
                    // PlaywrightUtil.sleep(1);
                } catch (Exception scrollError) {
                    log.warn("JavaScript滚动失败，尝试页面滚动: {}", scrollError.getMessage());
                    // 备用方案：滚动页面到大概位置
                    page.evaluate("window.scrollBy(0, " + (i * 200) + ")");
                    // PlaywrightUtil.sleep(1);
                }
                
                // 查找HR区域 - 尝试多种可能的HR标签选择器
                Locator hrArea = null;
                String[] hrSelectors = {
                    ".recruiter-info-box",  // 根据页面源码，这是主要的HR区域类名
                    ".recruiter-info, .hr-info, .contact-info",
                    "[class*='recruiter'], [class*='hr-'], [class*='contact']",
                    ".job-card-footer, .card-footer",
                    ".job-bottom, .bottom-info"
                };
                
                for (String selector : hrSelectors) {
                    Locator tempHrArea = currentJobCard.locator(selector);
                    if (tempHrArea.count() > 0) {
                        hrArea = tempHrArea.first();
                        log.debug("找到HR区域，使用选择器: {}", selector);
                        break;
                    }
                }
                
                // 如果找不到特定的HR区域，使用整个卡片
                if (hrArea == null) {
                    log.debug("未找到特定HR区域，使用整个岗位卡片");
                    hrArea = currentJobCard;
                }
                
                // 鼠标悬停到HR区域，触发按钮显示 - 简化悬停逻辑
                boolean hoverSuccess = false;
                int hoverRetries = 3;
                for (int retry = 0; retry < hoverRetries; retry++) {
                    try {
                        // 检查HR区域是否可见，如果不可见则跳过悬停
                        if (!hrArea.isVisible()) {
                            log.debug("HR区域不可见，跳过悬停操作");
                            hoverSuccess = true; // 设为成功，继续后续流程
                            break;
                        }
                        
                        // 直接悬停，不再进行复杂的微调
                        hrArea.hover(new Locator.HoverOptions().setTimeout(5000));
                        hoverSuccess = true;
                        break;
                    } catch (Exception hoverError) {
                        log.warn("第{}次悬停失败: {}", retry + 1, hoverError.getMessage());
                        if (retry < hoverRetries - 1) {
                            // 重试前重新滚动确保元素可见
                            try {
                                page.evaluate("(element) => element.scrollIntoView({behavior: 'instant', block: 'center'})", currentJobCard.elementHandle());
                                Thread.sleep(500); // 等待滚动完成
                            } catch (Exception e) {
                                log.warn("重试前滚动失败: {}", e.getMessage());
                            }
                        }
                    }
                }
                
                if (!hoverSuccess) {
                    log.warn("悬停操作失败，但继续查找按钮");
                    // 不再跳过，而是继续查找按钮，因为有些按钮可能不需要悬停就能显示
                }
                
                // PlaywrightUtil.sleep(1); // 等待按钮显示
                
                // 获取hr名字
                try {
                    Locator hrNameElement = currentJobCard.locator(".recruiter-name, .hr-name, .contact-name, [class*='recruiter-name'], [class*='hr-name']");
                    if (hrNameElement.count() > 0) {
                        recruiterName = hrNameElement.first().textContent();
                    } else {
                        recruiterName = "HR";
                    }
                } catch (Exception e) {
                    log.error("获取HR名字失败: {}", e.getMessage());
                    recruiterName = "HR";
                }
                
            } catch (Exception e) {
                log.error("处理岗位卡片失败: {}", e.getMessage());
                continue;
            }
            
            // 查找聊一聊按钮
            Locator button = null;
            String buttonText = "";
            try {
                // 在当前岗位卡片中查找按钮，尝试多种选择器
                Locator currentJobCard = page.locator(JOB_CARDS).nth(i);
                
                String[] buttonSelectors = {
                    "button.ant-btn.ant-btn-primary.ant-btn-round",
                    "button.ant-btn.ant-btn-round.ant-btn-primary", 
                    "button[class*='ant-btn'][class*='primary']",
                    "button[class*='ant-btn'][class*='round']",
                    "button[class*='chat'], button[class*='talk']",
                    ".chat-btn, .talk-btn, .contact-btn",
                    "button:has-text('聊一聊')",
                    "button" // 最后尝试所有按钮
                };
                
                for (String selector : buttonSelectors) {
                    try {
                        Locator tempButtons = currentJobCard.locator(selector);
                        int buttonCount = tempButtons.count();
                        log.debug("选择器 '{}' 找到 {} 个按钮", selector, buttonCount);
                        
                        for (int j = 0; j < buttonCount; j++) {
                            Locator tempButton = tempButtons.nth(j);
                            try {
                                if (tempButton.isVisible()) {
                                    String text = tempButton.textContent();
                                    log.debug("按钮文本: '{}'", text);
                                    if (text != null && !text.trim().isEmpty()) {
                                        button = tempButton;
                                        buttonText = text.trim();
                                        // 只关注"聊一聊"按钮
                                        if (text.contains("聊一聊")) {
                                            log.debug("找到目标按钮: '{}'", text);
                                            break;
                                        }
                                    }
                                }
                            } catch (Exception ignore) {
                                log.debug("获取按钮文本失败: {}", ignore.getMessage());
                            }
                        }
                        
                        if (button != null && buttonText.contains("聊一聊")) {
                            break;
                        }
                    } catch (Exception e) {
                        log.debug("选择器 '{}' 查找失败: {}", selector, e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                log.error("查找按钮失败: {}", e.getMessage());
                // 保存页面源码用于调试
                savePageSource(page, "button_search_failed");
                continue;
            }
            
            // 检查按钮文本并点击
            if (button != null && buttonText.contains("聊一聊")) {
                try {
                    // 在点击按钮前进行鼠标微调，先向右移动2像素，再向左移动2像素
                    try {
                        var boundingBox = button.boundingBox();
                        if (boundingBox != null) {
                            double centerX = boundingBox.x + boundingBox.width / 2;
                            double centerY = boundingBox.y + boundingBox.height / 2;
                            
                            // 先移动到按钮中心
                            page.mouse().move(centerX, centerY);
                            Thread.sleep(50);
                            
                            // 向右移动2像素
                            page.mouse().move(centerX + 2, centerY);
                            Thread.sleep(50);
                            
                            // 向左移动2像素（回到中心再向左2像素）
                            page.mouse().move(centerX - 2, centerY);
                            Thread.sleep(50);
                            
                            // 回到中心位置
                            page.mouse().move(centerX, centerY);
                            Thread.sleep(50);
                            
                            log.debug("完成鼠标微调，准备点击按钮");
                        }
                    } catch (Exception moveError) {
                        log.warn("鼠标微调失败，直接点击按钮: {}", moveError.getMessage());
                    }
                    
                    button.click();
                    // 增加等待时间，确保聊天窗口完全加载
                    Thread.sleep(2000);
                    
                    try {
                        // 等待聊天界面加载
                        page.waitForSelector(CHAT_HEADER, new Page.WaitForSelectorOptions().setTimeout(5000));
                        log.debug("聊天窗口已加载");
                        
                        // 明确发送打招呼消息，参考liepin_test.java的实现
                        Locator chatTextarea = page.locator(CHAT_TEXTAREA);
                        if (chatTextarea.count() > 0) {
                            // 先清除输入框，确保干净
                            chatTextarea.clear();
                            Thread.sleep(300);
                            
                            // 输入打招呼内容
                            String greeting = "您好，我对这个岗位很感兴趣，期待与您进一步沟通！";
                            chatTextarea.fill(greeting);
                            log.debug("已输入打招呼消息: {}", greeting);
                            Thread.sleep(500);
                            
                            // 重点改进：直接使用Enter键发送消息，这是最可靠的方式
                            chatTextarea.press("Enter");
                            log.debug("已按Enter键发送消息");
                            
                            // 给足够时间让消息发送完成
                            Thread.sleep(2000);
                            log.debug("等待消息发送完成");
                        }
                        
                        // 关闭聊天窗口
                        Locator close = page.locator(CHAT_CLOSE);
                        if (close.count() > 0) {
                            Thread.sleep(500);
                            close.click();
                            log.debug("已关闭聊天窗口");
                        }
                        
                        resultList.add(sb.append("【").append(companyName).append(" ").append(jobName).append(" ").append(salary).append(" ").append(recruiterName).append(" ").append("】").toString());
                        sb.setLength(0);
                        log.info("成功发送打招呼消息:【{}】的【{}·{}】岗位", companyName, jobName, salary);
                        
                    } catch (Exception e) {
                        log.warn("聊天窗口处理失败: {}", e.getMessage());
                        // 即使失败，也认为已尝试投递
                        resultList.add(sb.append("【").append(companyName).append(" ").append(jobName).append(" ").append(salary).append(" ").append(recruiterName).append(" ").append("】").toString());
                        sb.setLength(0);
                    }
                    
                    // 操作完成后等待一段时间，避免过快操作
                    PlaywrightUtil.sleep(1);
                    
                } catch (Exception e) {
                    log.error("点击按钮失败: {}", e.getMessage());
                    // 保存页面源码用于调试
                    savePageSource(page, "button_click_failed");
                }
            } else {
                if (button != null) {
                    log.debug("跳过岗位（按钮文本不匹配）: 【{}】的【{}·{}】岗位，按钮文本: '{}'", companyName, jobName, salary, buttonText);
                } else {
//                    log.warn("未找到可点击的按钮: 【{}】的【{}·{}】岗位", companyName, jobName, salary);
                    // 保存页面源码用于调试
                    savePageSource(page, "no_button_found");
                }
            }
            
            // 等待一下，避免操作过快
            // PlaywrightUtil.sleep(1);
        }
    }

    @SneakyThrows
    private static void login() {
        log.info("正在打开猎聘网站...");
        Page page = PlaywrightUtil.getPageObject("liepin");
        page.navigate(homeUrl);
        log.info("猎聘正在登录...");
        
        if (PlaywrightUtil.isCookieValid(cookiePath)) {
            PlaywrightUtil.loadCookies(cookiePath, "liepin");
            page.reload();
        }
        
        page.waitForSelector(HEADER_LOGO, new Page.WaitForSelectorOptions().setTimeout(10000));
        
        if (isLoginRequired()) {
            log.info("cookie失效，尝试扫码登录...");
            scanLogin();
            PlaywrightUtil.saveCookies(cookiePath, "liepin");
        } else {
            log.info("cookie有效，准备投递...");
        }
    }

    private static boolean isLoginRequired() {
        Page page = PlaywrightUtil.getPageObject("liepin");
        String currentUrl = page.url();
        return !currentUrl.contains("c.liepin.com");
    }

    private static void scanLogin() {
        try {
            Page page = PlaywrightUtil.getPageObject("liepin");
            
            // 点击切换登录类型按钮
            Locator switchBtn = page.locator(LOGIN_SWITCH_BTN);
            if (switchBtn.count() > 0) {
                switchBtn.click();
            }
            
            log.info("等待扫码..");

            // 记录开始时间
            long startTime = System.currentTimeMillis();
            long maxWaitTime = 20 * 60 * 1000; // 20分钟，单位毫秒（用户要求延长）
            
            // 增加额外的登录状态选择器
            String[] additionalLoginSelectors = {
                "//div[contains(@class, 'user-info')]",
                "//div[contains(@class, 'user-avatar')]",
                "//span[contains(text(), '我的')]",
                "//a[contains(@href, '/user/')]",
                "//div[@id='header-quick-menu']"
            };

            // 主循环，直到登录成功或超时
            while (true) {
                boolean loginSuccess = false;
                String successReason = "";
                
                try {
                    // 检查URL是否包含登录成功的标识
                    String currentUrl = page.url();
                    if (currentUrl.contains("c.liepin.com") || currentUrl.contains("user")) {
                        successReason = "URL包含登录成功标识: " + currentUrl;
                        loginSuccess = true;
                    }
                    
                    // 检查方式1：登录按钮文本变化
                    if (!loginSuccess) {
                        try {
                            Locator loginButtons = page.locator(LOGIN_BUTTONS);
                            if (loginButtons.count() > 0) {
                                String buttonText = loginButtons.first().textContent();
                                log.debug("登录按钮文本: {}", buttonText);
                                if (!buttonText.contains("登录")) {
                                    successReason = "登录按钮文本变化: " + buttonText;
                                    loginSuccess = true;
                                }
                            }
                        } catch (Exception e) {
                            log.debug("检查方式1失败: {}", e.getMessage());
                        }
                    }
                    
                    // 检查方式2：用户信息元素
                    if (!loginSuccess) {
                        try {
                            Locator userInfo = page.locator(USER_INFO);
                            if (userInfo.count() > 0) {
                                String infoText = userInfo.first().textContent();
                                log.debug("用户信息文本: {}", infoText);
                                if (infoText.contains("你好") || !infoText.isEmpty()) {
                                    successReason = "用户信息元素存在: " + infoText;
                                    loginSuccess = true;
                                }
                            }
                        } catch (Exception e) {
                            log.debug("检查方式2失败: {}", e.getMessage());
                        }
                    }
                    
                    // 检查方式3：尝试额外的选择器
                    if (!loginSuccess) {
                        for (String selector : additionalLoginSelectors) {
                            try {
                                Locator element = page.locator(selector);
                                if (element.count() > 0) {
                                    String text = element.first().textContent();
                                    log.debug("额外选择器[{}]匹配到元素: {}", selector, text);
                                    successReason = "额外选择器匹配成功: " + selector;
                                    loginSuccess = true;
                                    break;
                                }
                            } catch (Exception e) {
                                log.debug("额外选择器[{}]检查失败: {}", selector, e.getMessage());
                            }
                        }
                    }
                    
                    // 如果找到任何登录成功的迹象，就认为登录成功
                    if (loginSuccess) {
                        log.info("用户扫码成功，继续执行... 原因: {}", successReason);
                        break;
                    }
                    
                    // 打印当前页面URL和标题，帮助调试
                    if (System.currentTimeMillis() - startTime > 5000 && 
                        (System.currentTimeMillis() - startTime) % 10000 < 1000) {
                        log.debug("等待登录中... 当前URL: {}, 标题: {}", 
                                 page.url(), page.title());
                    }
                    
                } catch (Exception e) {
                    log.error("检查登录状态时发生异常: {}", e.getMessage());
                }

                // 检查是否超过最大等待时间
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime > maxWaitTime) {
                        log.error("登录超时，20分钟内未完成扫码登录，程序将退出。");
                    PlaywrightUtil.close(); // 关闭浏览器
                    return; // 返回而不是退出整个程序
                }
                
                // 增加等待时间，减少CPU占用
                Thread.sleep(2000);
            }

            // 登录成功后，保存Cookie
            PlaywrightUtil.saveCookies(cookiePath, "liepin");
            log.info("登录成功，Cookie已保存。");

        } catch (Exception e) {
            log.error("scanLogin() 失败: {}", e.getMessage());
            PlaywrightUtil.close(); // 关闭浏览器
            return; // 返回而不是退出整个程序
        }
    }



}
