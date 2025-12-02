package ai;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
public class AiService {

    private static final Dotenv dotenv = Dotenv.load();
    private static final String BASE_URL = dotenv.get("BASE_URL") + "/v1/chat/completions";
    private static final String API_KEY = dotenv.get("API_KEY");
    private static final String MODEL = dotenv.get("MODEL");


    public static String sendRequest(String content) {
        // 设置超时时间，单位：秒
        int timeoutInSeconds = 60;  // 你可以修改这个变量来设置超时时间

        // 创建 HttpClient 实例并设置超时
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutInSeconds))  // 设置连接超时
                .build();

        // 构建 JSON 请求体
        JSONObject requestData = new JSONObject();
        requestData.put("model", MODEL);
        requestData.put("temperature", 0.5);

        // 添加消息内容
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", content);
        messages.put(message);

        requestData.put("messages", messages);

        // 构建 HTTP 请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestData.toString()))
                .build();

        // 创建线程池用于执行请求
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<HttpResponse<String>> task = () -> client.send(request, HttpResponse.BodyHandlers.ofString());

        // 提交请求并控制超时
        Future<HttpResponse<String>> future = executor.submit(task);
        try {
            // 使用 future.get 设置超时
            HttpResponse<String> response = future.get(timeoutInSeconds, TimeUnit.SECONDS);

            if (response.statusCode() == 200) {
                // 解析响应体
                log.info(response.body());
                JSONObject responseObject = new JSONObject(response.body());
                String requestId = responseObject.getString("id");
                long created = responseObject.getLong("created");
                String model = responseObject.getString("model");

                // 解析返回的内容
                JSONObject messageObject = responseObject.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message");
                String responseContent = messageObject.getString("content");

                // 解析 usage 部分
                JSONObject usageObject = responseObject.getJSONObject("usage");
                int promptTokens = usageObject.getInt("prompt_tokens");
                int completionTokens = usageObject.getInt("completion_tokens");
                int totalTokens = usageObject.getInt("total_tokens");

                // 格式化时间
                LocalDateTime createdTime = Instant.ofEpochSecond(created)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String formattedTime = createdTime.format(formatter);

                log.info("请求ID: {}, 创建时间: {}, 模型名: {}, 提示词: {}, 补全: {}, 总用量: {}", requestId, formattedTime, model, promptTokens, completionTokens, totalTokens);
                return responseContent;
            } else {
                log.error("AI请求失败！状态码: {}", response.statusCode());
            }
        } catch (TimeoutException e) {
            log.error("请求超时！超时设置为 {} 秒", timeoutInSeconds);
        } catch (Exception e) {
            log.error("AI请求异常！", e);
        } finally {
            executor.shutdownNow();  // 关闭线程池
        }
        return "";
    }


    public static void main(String[] args) {
        System.out.println(cleanBossDesc(".EwyXFHpFfseN{display:inline-block;width:0.1px;height:0.1px;overflow:hidden;visibility: hidden;}.FxpRjMznwNS{display:inline-block;font-size:0!important;width:1em;height:1em;visibility:hidden;line-height:0;}.QTsRdnap{display:inline-block;font-size:0!important;width:1em;height:1em;visibility:hidden;line-height:0;}.spBzTCGii{display:inline-block;font-size:0!important;width:1em;height:1em;visibility:hidden;line-height:0;}.DXpfskbRdfn{display:inline-block;width:0.1px;height:0.1px;overflow:hidden;visibility: hidden;}.snNcSPFFs{font-style:normal;font-weight:normal}.zjziXGAdnjK{font-style:normal;font-weight:normal}.CjmzfkfTmx{font-style:normal;font-weight:normal}.YYTWRZHhrm{font-style:normal;font-weight:normal}.cfAzXEKs{font-style:normal;font-weight:normal}岗位职责：\n" +
                "一、客户支持与业务拓展\n" +
                "1、负责客户售前支持、行业拓展、行业洞察工作；\n" +
                "2、负责信息安全综合性项目的售前技术交流及方案撰写、招投标等工作；\n" +
                "3、负责结合公司产品特点，引导用户需求，规划、设计、制定解决方案；\n" +
                "4、配合销售完成业务目标。\n" +
                "二、解决方案设计与客户成功\n" +
                "1、负责公司安全解决方案设计，输出行业标杆案例、技术白皮书及标准化营销材料（PPT/场景化Demo）;\n" +
                "2、跟进重点客户项目全流程进展，联合售前、交付团队确保技术方案落地，预判并管控风险，保障客户成功与满意度；\n" +
                "3、对售前/交付团队的技能培训和推广，提升团队技术专业性，协助制定相关技术考核制度；\n" +
                "4、搭建客户成功案例库，制作可复用的技术营销材料，确保产品价值高效精准展现；\n" +
                "5、参与关键商机、关键产品的POC过程追踪，协调资源，提前管控风险。\n" +
                "\n" +
                "任职要求：\n" +
                "一、教育背景\n" +
                "本科以上学历，计算机、网络等相关专业。\n" +
                "二、工作经验\n" +
                "1、3年以上信息安全工作经验，具5-8年以上B端客户成功/销售支持经验优先；\n" +
                "2、熟悉国家或行业信息安全标准、具备较为完整的信息安全知识体系；\n" +
                "3、具有优秀的安全解决方案的编写和讲解能力。\n" +
                "三、专业知识\n" +
                "1、对网络信息安全理论有比较深入的认识，熟悉网络设备、操作系统技术原理；\n" +
                "2、对云计算、移动互联、大数据、物联网等领域有一定了解；\n" +
                "3、熟悉主流信息安全产品（WAF、态势感知、杀毒软件、防毒墙、流量探针、数据安全等）；\n" +
                "4、熟悉安全产品或信息安全领域知识，能够理解和传达产品的技术价值和优势；\n" +
                "5、熟悉网络安全产品逻辑，能快速拆解客户业务场景与技术需求的关联。\n" +
                "四、能力要求\n" +
                "1、具备良好新业务学习能力、演讲能力、沟通能力；\n" +
                "2、擅长跨部门资源整合，具备一定的沟通表达能力和数据敏感度；\n" +
                "3、出色的沟通表达逻辑，良好的学习能力，自驱力强；\n" +
                "4、具有CISP、CISA、CISP等相关资质优先，具有对中大型企业安全规划能力优先，文笔优秀优先；\n" +
                "5、具备PMP/CSM认证或PoC交付经验者优先。"));

        try {
            // 示例：发送请求
            String content = "你好";
            String response = sendRequest(content);
            System.out.println("AI回复: " + response);
        } catch (Exception e) {
            log.error("AI异常！");
        }
    }

    public static String cleanBossDesc(String raw) {
        return raw.replaceAll("kanzhun|BOSS直聘|来自BOSS直聘", "")
                .replaceAll("[\\u200b-\\u200d\\uFEFF]", "")
                .replaceAll("<[^>]+>", "") // 如果有HTML标签就用
                .replaceAll("\\s+", " ")
                .trim();
    }
}
