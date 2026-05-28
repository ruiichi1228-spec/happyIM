package com.happyim.common.service;

import com.happyim.common.mapper.SensitiveWordMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 敏感词 + URL 过滤器。
 * 先过滤 URL 链接，再用 AC 自动机匹配敏感词，匹配到的内容替换为 ***
 */
@Component
public class SensitiveWordFilter implements MessageFilter {

    // 匹配 URL 的正则：http/https、www 域名、以及裸域名
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^\\s]+" +
            "|www\\.[^\\s]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?" +
            "|[a-zA-Z0-9][-a-zA-Z0-9]*\\.[a-zA-Z]{2,}(?:/[^\\s]*)?",
            Pattern.CASE_INSENSITIVE);

    private volatile AhoCorasick ac;
    private final SensitiveWordMapper sensitiveWordMapper;

    public SensitiveWordFilter(SensitiveWordMapper sensitiveWordMapper) {
        this.sensitiveWordMapper = sensitiveWordMapper;
        this.ac = new AhoCorasick();
        try {
            loadWords();
        } catch (Exception e) {
            ac.build(List.of("广告", "违禁词"));
        }
    }

    private void loadWords() {
        if (sensitiveWordMapper == null) {
            ac.build(List.of("广告", "违禁词"));
            return;
        }
        List<String> words = sensitiveWordMapper.findAllWords();
        if (words == null || words.isEmpty()) {
            words = List.of("广告", "违禁词");
        }
        ac.build(words);
    }

    public synchronized void reload() {
        try {
            loadWords();
        } catch (Exception e) {
            // table not created yet, ignore
        }
    }

    @Override
    public String doFilter(String content) throws FilterRejectException {
        // 第一层：过滤所有 URL 链接
        content = URL_PATTERN.matcher(content).replaceAll("***");

        // 第二层：AC 自动机匹配敏感词
        List<AhoCorasick.Match> hits = ac.search(content);
        if (hits.isEmpty()) return content;

        StringBuilder sb = new StringBuilder(content);
        for (int i = hits.size() - 1; i >= 0; i--) {
            AhoCorasick.Match hit = hits.get(i);
            sb.replace(hit.start, hit.end, "***");
        }
        return sb.toString();
    }

    /**
     * AC 自动机实现
     */
    static class AhoCorasick {
        private Node root = new Node();

        static class Node {
            Map<Character, Node> children = new HashMap<>();
            Node fail;
            String word; // 非 null 表示是一个模式串的结尾
        }

        static class Match {
            int start, end;
            Match(int s, int e) { start = s; end = e; }
        }

        void build(List<String> words) {
            // 构建 Trie
            for (String w : words) {
                Node cur = root;
                for (char c : w.toCharArray()) {
                    cur = cur.children.computeIfAbsent(c, k -> new Node());
                }
                cur.word = w;
            }
            // 构建 fail 指针 (BFS)
            Queue<Node> queue = new LinkedList<>();
            root.fail = root;
            for (Node child : root.children.values()) {
                child.fail = root;
                queue.add(child);
            }
            while (!queue.isEmpty()) {
                Node cur = queue.poll();
                for (Map.Entry<Character, Node> e : cur.children.entrySet()) {
                    char c = e.getKey();
                    Node child = e.getValue();
                    Node fail = cur.fail;
                    while (fail != root && !fail.children.containsKey(c)) {
                        fail = fail.fail;
                    }
                    if (fail.children.containsKey(c) && fail.children.get(c) != child) {
                        child.fail = fail.children.get(c);
                    } else {
                        child.fail = root;
                    }
                    queue.add(child);
                }
            }
        }

        List<Match> search(String text) {
            List<Match> result = new ArrayList<>();
            Node cur = root;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                while (cur != root && !cur.children.containsKey(c)) {
                    cur = cur.fail;
                }
                if (cur.children.containsKey(c)) {
                    cur = cur.children.get(c);
                }
                // 检查当前节点及其 fail 链上是否有匹配
                for (Node node = cur; node != root; node = node.fail) {
                    if (node.word != null) {
                        result.add(new Match(i - node.word.length() + 1, i + 1));
                    }
                }
            }
            return result;
        }
    }
}
