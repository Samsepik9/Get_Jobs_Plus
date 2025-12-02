package ai;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.json.JSONArray;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * AI服务测试类，用于验证环境变量和API配置
 */
@Slf4j
public class AiServiceTest {

    public static void main(String[] args) {
        System.out.println("开始测试AI服务连接...");
        
        try {
            // 1. 测试环境变量加载
            System.out.println("\n1. 测试环境变量加载：");
            Dotenv dotenv = Dotenv.load();
            
            String baseUrl = dotenv.get("BASE_URL");
            String apiKey = dotenv.get("API_KEY");
            String model = dotenv.get("MODEL");
            String hookUrl = dotenv.get("HOOK_URL");
            
            System.out.println("BASE_URL: " + (baseUrl != null ? baseUrl.substring(0, 10) + "..." : "未设置"));
            System.out.println("API_KEY: " + (apiKey != null ? apiKey.substring(0, 5) + "..." : "未设置"));
            System.out.println("MODEL: " + (model != null ? model : "未设置"));
            System.out.println("HOOK_URL: " + (hookUrl != null ? hookUrl.substring(0, 10) + "..." : "未设置"));
            
            if (baseUrl == null || apiKey == null || model == null) {
                System.out.println("\n❌ 错误：缺少必要的环境变量配置!");
                return;
            }
            
            // 2. 测试API连接
            System.out.println("\n2. 测试API连接...");
            String fullUrl = baseUrl + "/v1/chat/completions";
            
            // 创建简单的请求体
            JSONObject requestData = new JSONObject();
            requestData.put("model", model);
            requestData.put("temperature", 0.5);
            
            // 添加简单的消息（使用JSONArray格式）
            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", "你好，请简单回复一条消息验证连接");
            messages.put(message);
            
            requestData.put("messages", messages);
            
            String requestBody = requestData.toString();
            System.out.println("请求体预览：" + requestBody.substring(0, Math.min(100, requestBody.length())) + "...");
            
            // 创建HTTP客户端
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            
            // 构建请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            
            System.out.println("正在发送请求到：" + fullUrl);
            
            // 发送请求并获取详细响应
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("\n3. 响应结果：");
            System.out.println("状态码: " + response.statusCode());
            
            // 修复响应头处理方式
            System.out.println("响应头数量: " + response.headers().map().size());
            
            System.out.println("\n响应体: ");
            String responseBody = response.body();
            System.out.println(responseBody.substring(0, Math.min(500, responseBody.length())) + 
                               (responseBody.length() > 500 ? "..." : ""));
            
            if (response.statusCode() == 200) {
                System.out.println("\n✅ 测试成功！AI服务连接正常");
            } else {
                System.out.println("\n❌ 测试失败！状态码: " + response.statusCode());
                System.out.println("建议检查：");
                System.out.println("1. BASE_URL 是否正确");
                System.out.println("2. API_KEY 是否有效");
                System.out.println("3. MODEL 是否支持");
                System.out.println("4. 请求格式是否符合API要求");
            }
            
        } catch (Exception e) {
            System.out.println("\n❌ 测试过程中发生异常：");
            e.printStackTrace();
            System.out.println("\n可能的原因：");
            System.out.println("1. 网络连接问题");
            System.out.println("2. .env文件未正确加载");
            System.out.println("3. API端点不可达");
        }
    }
}