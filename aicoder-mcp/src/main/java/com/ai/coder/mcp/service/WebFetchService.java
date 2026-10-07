package com.ai.coder.mcp.service;

import com.ai.coder.mcp.config.McpProperties;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.URI;

/** 网页抓取：Jsoup 取正文 HTML → flexmark 转 markdown。SSRF 防护：禁内网地址。 */
@Slf4j
@Service
public class WebFetchService {

    private final RestTemplate restTemplate;
    private final McpProperties props;
    private final FlexmarkHtmlConverter htmlToMarkdown;

    public WebFetchService(@Qualifier("plainRestTemplate") RestTemplate restTemplate, McpProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
        // flexmark html2md API 调整：FlexmarkHtmlConverter 上无 EXTENSIONS 数据键，
        // 通过 Builder（继承自 BuilderBase）的 extensions(Collection) 注册扩展。
        // 表格转换由 html2md-converter 内置（TABLE_NODE 等），无需额外 TablesExtension。
        this.htmlToMarkdown = FlexmarkHtmlConverter.builder().build();
    }

    public String fetch(String url) {
        if (!isAllowed(url)) {
            throw new IllegalArgumentException("拒绝抓取内网/本地地址：" + url);
        }
        try {
            String html = restTemplate.getForObject(url, String.class);
            if (html == null || html.isBlank()) {
                return "网页内容为空。";
            }
            int max = props.getFetch().getMaxLength();
            if (html.length() > max) {
                html = html.substring(0, max);
            }
            return convertToMarkdown(html);
        } catch (IllegalArgumentException e) {
            // isAllowed 已抛出的 IllegalArgumentException 不要被下面的 catch 吞掉
            throw e;
        } catch (Exception e) {
            log.warn("web_fetch 失败 url={}", url, e);
            return "网页抓取失败：" + e.getMessage();
        }
    }

    /** SSRF 防护：拒绝 localhost 与私有/回环 IP。allow-private-ip=true 时放行（仅开发用）。 */
    public boolean isAllowed(String url) {
        if (props.getFetch().isAllowPrivateIp()) {
            return true;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;
            if ("localhost".equalsIgnoreCase(host)) return false;
            InetAddress addr = InetAddress.getByName(host);
            return !(addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress() || addr.isLinkLocalAddress());
        } catch (Exception e) {
            return false;
        }
    }

    public String convertToMarkdown(String html) {
        String clean = Jsoup.clean(html, "", Safelist.relaxed());
        return htmlToMarkdown.convert(clean).trim();
    }
}
