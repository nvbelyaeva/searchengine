package searchengine.utility;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;

@Getter
@Setter
public class TextAnalyzer {
    @Value("${snippet.length}")
    private static int snippetLength;
    @Value("${snippet.indent}")
    private static int snippetIndent;

    public static HashMap<String, Integer> getLemmas(String text) {

        LuceneMorphology luceneMorph;
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        HashMap<String, Integer> map = new HashMap<>();
        String textRu = text.toLowerCase().replaceAll("[^А-Яа-яЁё]+", " ").replaceAll("[\\s]{2,}", " ");
        String[] words = textRu.split(" ");
        for (String word : words) {
            try {
                List<String> wordNormalForms = luceneMorph.getNormalForms(word);
                List<String> wordMorphInfo = luceneMorph.getMorphInfo(word);
                if (wordMorphInfo == null || wordNormalForms == null || wordMorphInfo.isEmpty() || wordNormalForms.isEmpty()) {
                    continue;
                }
                String normalForm = wordNormalForms.get(0);
                String morphInfo = wordMorphInfo.get(0);
                if (morphInfo.matches(".*ПРЕДЛ.*|.*СОЮЗ.*|.*МЕЖД.*")) {
                    continue;
                }
                if (map.containsKey(normalForm)) {
                    map.put(normalForm, map.get(normalForm) + 1);
                } else {
                    map.put(normalForm, 1);
                }
            } catch (Exception e) {
            }
        }
        return map;
    }

    public static String getTextWithoutHtmlTags(String text) {
        return text.replaceAll("<[^<>]+>", " ").replaceAll("[\\s]{2,}", " ");
    }

    public static String getSnippets(String content, Set<String> lemmas) {
        LuceneMorphology luceneMorph;
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Document document = Jsoup.parse(content);
        String text = document.text();
        String textRu = text.replaceAll("[^А-Яа-яЁё]+", " ").replaceAll("[\\s]{2,}", " ");
        String[] words = textRu.split(" ");
        Set<String> replaceWordsSet = Arrays.stream(words).filter(word -> {
            List<String> wordNormalForms;
            try {
                wordNormalForms = luceneMorph.getNormalForms(word.toLowerCase());
            }
            catch(Exception ex) {
                return false;
            }
            if (wordNormalForms == null || wordNormalForms.isEmpty()) {
                return false;
            }
            String normalForm = wordNormalForms.get(0);
            return lemmas.contains(normalForm);
        }).collect(Collectors.toSet());
        for (String word : replaceWordsSet) {
            String replaceWord = "<b>" + word + "</b>";
            text = text.replaceAll(word, replaceWord);
        }
        int firstPosition = text.indexOf("<b>");
        if (firstPosition < 0) {
            return "";
        }

        if (snippetIndent <= 0) {
            snippetIndent = 50;
        }
        if (snippetLength <= 0) {
            snippetLength = 500;
        }
        int startIndex = text.indexOf(" ", Math.max(firstPosition - snippetIndent, 0)) + 1;
        int lastIndex = text.lastIndexOf(" ", Math.min(firstPosition + snippetLength, text.length() - 1));
        if (startIndex < 0 || startIndex > lastIndex || lastIndex > text.length()) {
            return "";
        }
        return text.substring(startIndex, lastIndex);
    }

    public static String getPageTitle(String text) {
        Document document = Jsoup.parse(text);
        return document.title();
    }

}
