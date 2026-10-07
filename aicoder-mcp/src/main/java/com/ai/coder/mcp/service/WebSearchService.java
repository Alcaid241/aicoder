package com.ai.coder.mcp.service;

import com.ai.coder.mcp.config.McpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/** 网络搜索：调 Tavily Search API（外部 URL，用普通 RestTemplate）。 */
@Slf4j
@Service
public class WebSearchService {

    private static final String TAVILY_URL = "https://api.tavily.com/search";

    private final RestTemplate restTemplate;
    private final McpProperties props;

    public WebSearchService(@Qualifier("plainRestTemplate") RestTemplate restTemplate, McpProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
    }

    @SuppressWarnings("unchecked")
    public String search(String query) {
        String apiKey = props.getSearch().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "网络搜索未配置：缺少 mcp.search.api-key。请在 Nacos 配置 Tavily API key。";
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                    "api_key", apiKey,
                    "query", query,
                    "max_results", props.getSearch().getMaxResults());
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            Map<String, Object> resp = restTemplate.postForObject(TAVILY_URL, entity, Map.class);
            if (resp == null || !(resp.get("results") instanceof List<?> list)) {
                return "网络搜索无结果。";
            }
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Object o : list) {
                if (o instanceof Map<?, ?> raw) {
                    sb.append(i++).append(". ").append(str(raw.get("title")))
                      .append("\n   ").append(str(raw.get("url")))
                      .append("\n   ").append(str(raw.get("content"))).append("\n\n");
                }
            }
            return sb.length() == 0 ? "网络搜索无结果。" : sb.toString();
        } catch (Exception e) {
            log.warn("web_search 失败 query={}", query, e);
            return "网络搜索失败：" + e.getMessage();
        }
    }

    /** null 安全的字符串化（Tavily 返回字段可能缺失）。 */
    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
