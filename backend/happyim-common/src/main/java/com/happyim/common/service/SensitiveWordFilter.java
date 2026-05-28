package com.happyim.common.service;

import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import com.happyim.common.mapper.SensitiveWordMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.TreeMap;

/**
 * 基于 Hankcs AhoCorasickDoubleArrayTrie 的敏感词过滤器。
 * 匹配到的敏感词替换为 ***
 */
@Component
public class SensitiveWordFilter implements MessageFilter {

    private volatile AhoCorasickDoubleArrayTrie<String> acdat;
    private final SensitiveWordMapper sensitiveWordMapper;

    public SensitiveWordFilter(SensitiveWordMapper sensitiveWordMapper) {
        this.sensitiveWordMapper = sensitiveWordMapper;
        try {
            loadWords();
        } catch (Exception e) {
            buildFromDefaults();
        }
    }

    private void loadWords() {
        if (sensitiveWordMapper == null) {
            buildFromDefaults();
            return;
        }
        List<String> words = sensitiveWordMapper.findAllWords();
        if (words == null || words.isEmpty()) {
            buildFromDefaults();
            return;
        }
        TreeMap<String, String> map = new TreeMap<>();
        for (String w : words) map.put(w, w);
        AhoCorasickDoubleArrayTrie<String> trie = new AhoCorasickDoubleArrayTrie<>();
        trie.build(map);
        this.acdat = trie;
    }

    private void buildFromDefaults() {
        TreeMap<String, String> map = new TreeMap<>();
        for (String w : List.of("广告", "违禁词")) map.put(w, w);
        AhoCorasickDoubleArrayTrie<String> trie = new AhoCorasickDoubleArrayTrie<>();
        trie.build(map);
        this.acdat = trie;
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
        if (acdat == null) return content;

        List<AhoCorasickDoubleArrayTrie.Hit<String>> hits = acdat.parseText(content);
        if (hits.isEmpty()) return content;

        StringBuilder sb = new StringBuilder(content);
        for (int i = hits.size() - 1; i >= 0; i--) {
            AhoCorasickDoubleArrayTrie.Hit<String> hit = hits.get(i);
            sb.replace(hit.begin, hit.end, "***");
        }
        return sb.toString();
    }
}
