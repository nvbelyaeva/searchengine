package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.config.UserSettings;
import searchengine.dto.IndexErrorResponse;
import searchengine.dto.IndexResponse;
import searchengine.dto.search.SearchItem;
import searchengine.dto.search.SearchResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.StatusType;
import searchengine.model.Website;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utility.TextAnalyzer;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList siteList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final UserSettings userSettings;
    @Value("${search.frequency-limit}")
    private int frequencyLimit;
    public ForkJoinPool pool;
    private Vector<Inspector> runningTasksVector = new Vector<>();
    AtomicBoolean stopRunning = new AtomicBoolean(false);

    public IndexResponse startIndexing() {
        for (Site site : siteList.getSites()) {
            Website website = siteRepository.findByUrl(site.getUrl());
            if (website != null) {
                if (website.getStatus().equals(StatusType.INDEXING)) {
                    return new IndexErrorResponse(false, "Индексация уже запущена");
                } else {
                    delete(website);
                }
            }
            Thread tread = new Thread(new Starter(site, "/", true));
            tread.start();
        }
        return new IndexResponse(true);
    }

    private void delete(Website website) {
        List<Index> indexesForDelete = indexRepository.findByWebsiteId(website.getId());
        indexRepository.deleteAll(indexesForDelete);
        siteRepository.delete(website);
    }

    @RequiredArgsConstructor
    private class Starter implements Runnable {
        private final Site site;
        private final String path;
        private final boolean includeChildPage;

        @Override
        public void run() {
            try {
                Website website = siteRepository.findByUrl(site.getUrl());
                if (website == null) {
                    website = new Website();
                    website.setName(site.getName());
                    website.setUrl(site.getUrl());
                }
                website.setStatus(StatusType.INDEXING);
                website.setStatusTime(LocalDateTime.now());
                siteRepository.save(website);

                if (pool == null) {
                    pool = new ForkJoinPool();
                    stopRunning.set(false);
                }
                Inspector task = new Inspector(website.getUrl() + path, includeChildPage);
                runningTasksVector.add(task);
                pool.invoke(task);
                runningTasksVector.remove(task);
                if (!stopRunning.get()) {
                    website.setStatus(StatusType.INDEXED);
                }
                siteRepository.save(website);
                if (runningTasksVector.size() == 0 && pool != null) {
                    pool.shutdown();
                    pool = null;
                    System.out.println("Задача остановлена");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @RequiredArgsConstructor
    public class Inspector extends RecursiveAction {
        private final String address;
        private final boolean includeChildPage;

        @Override
        protected void compute() {
            if (stopRunning.get()) {
                System.out.println("Поток остановлен");
                return;
            }
            System.out.println(address);
            URI baseURI = checkPageAddress(address);
            if (baseURI == null) {
                return;
            }
            Website website = siteRepository.findByUrl(baseURI.getScheme() + "://" + baseURI.getHost());
            if (website == null) {
                return;
            }
            Page page = savePage(baseURI, website);
            if (page == null) {
                return;
            }
            HashMap<String, Integer> lemmaMap = TextAnalyzer.getLemmas(TextAnalyzer.getTextWithoutHtmlTags(page.getContent()));
            saveLemmaMap(lemmaMap, website.getId(), page.getId());
            if (!includeChildPage) {
                return;
            }
            if (stopRunning.get()) {
                System.out.println("Поток остановлен");
                return;
            }
            Set<String> inspectSet;
            Document document = null;
            try {
                document = Jsoup.connect(address).userAgent(userSettings.getUserAgent()).referrer(userSettings.getReferrer()).get();
                inspectSet = getHrefAddresses(baseURI, document);
            } catch (IOException e) {
                return;
            }
            List<Page> existingPageList = pageRepository.findByPathList(website.getId(), inspectSet.stream().toList());
            existingPageList.stream().forEach(e -> inspectSet.remove(e.getPath()));
            for (String childAddress : inspectSet) {
                if (stopRunning.get()) {
                    System.out.println("Поток остановлен");
                    return;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
                Inspector task = new Inspector(childAddress, true);
                runningTasksVector.add(task);
                pool.invoke(task);
                runningTasksVector.remove(task);
            }
//            URI baseURI;
//            String domain;
//            try {
//                baseURI = new URI(address);
//                domain = baseURI.getHost();
//                if (!baseURI.getScheme().matches("https?")) {
//                    return;
//                }
//                if (domain == null || domain.isEmpty()) {
//                    return;
//                }
//                if (baseURI.getPath().matches(".*\s+.*")) {
//                    return;
//                }
//            } catch (URISyntaxException e) {
//                return;
//            }

//            HttpClient httpClient = HttpClient.newHttpClient();
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(baseURI)
//                    .GET()
//                    .build();
//            try {
//                if (stopRunning.get()) {
//                    System.out.println("Поток остановлен");
//                    return;
//                }

//                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//
//                HttpHeaders headers = response.headers();
//                Map<String, List<String>> headersMap = headers.map();
//                if (!headersMap.containsKey("content-type")) {
//                    return;
//                }
//                List<String> contentTypeList = headersMap.get("content-type");
//                if (contentTypeList.isEmpty() || !contentTypeList.get(0).matches("text/html.*")) {
//                    return;
//                }
//                String body = response.body();
//                int statusCode = response.statusCode();
//                if (statusCode < 200 || statusCode >= 300) {
//                    return;
//                }

//                Website website = siteRepository.findByUrl(baseURI.getScheme() + "://" + baseURI.getHost());
//                if (website == null) {
//                    return;
//                }

//                Page page = pageRepository.findByPath(website.getId(), baseURI.getPath());
//                if (page != null) {
//                    return;
//                }
//
//                if (stopRunning.get()) {
//                    System.out.println("Поток остановлен");
//                    return;
//                }
//
//                page = new Page();
//                page.setCode(statusCode);
//                page.setPath(baseURI.getPath());
//                page.setContent(body);
//                pageRepository.save(page);
//                page.setSite(website);
//                pageRepository.save(page);
//                website.setStatusTime(LocalDateTime.now());
//                siteRepository.save(website);

//                HashMap<String, Integer> lemmaMap = TextAnalyzer.getLemmas(TextAnalyzer.getTextWithoutHtmlTags(body));
//                saveLemmaMap(lemmaMap, website.getId(), page.getId());
//
//                if (!includeChildPage) {
//                    return;
//                }
//                if (stopRunning.get()) {
//                    System.out.println("Поток остановлен");
//                    return;
//                }

//                Set<String> inspectSet = new HashSet<>();
//                Document document = Jsoup.connect(address).userAgent(userSettings.getUserAgent()).referrer(userSettings.getReferrer()).get();

//                Elements aElements = document.select("a");
//                for (Element aElement : aElements) {
//                    String href = aElement.attr("href");
//                    URI resolvedURI;
//                    URI normalizedURI;
//                    try {
//                        resolvedURI = baseURI.resolve(href);
//                        normalizedURI = resolvedURI.normalize();
//                        if (!normalizedURI.getScheme().matches("https?")) {
//                            continue;
//                        }
//                        if (!normalizedURI.getHost().equals(domain)) {
//                            continue;
//                        }
//                        String inspectAddress = normalizedURI.getScheme() + "://" + normalizedURI.getHost() + normalizedURI.getPath();
//                        inspectSet.add(inspectAddress);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }

//                List<Page> existingPageList = pageRepository.findByPathList(website.getId(), inspectSet.stream().toList());
//                existingPageList.stream().forEach(e -> inspectSet.remove(e.getPath()));

//                for (String childAddress : inspectSet) {
//                    if (stopRunning.get()) {
//                        System.out.println("Поток остановлен");
//                        return;
//                    }
//                    try {
//                        Thread.sleep(100);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                        continue;
//                    }
//
//                    Inspector task = new Inspector(childAddress, true);
//                    runningTasksVector.add(task);
//                    pool.invoke(task);
//                    runningTasksVector.remove(task);
//                }

//            } catch (IOException | InterruptedException e) {
//                e.printStackTrace();
//            }
        }

        private URI checkPageAddress(String checkAddress) {
            URI baseURI;
            String domain;
            try {
                baseURI = new URI(checkAddress);
                domain = baseURI.getHost();
                if (!baseURI.getScheme().matches("https?")) {
                    return null;
                }
                if (domain == null || domain.isEmpty()) {
                    return null;
                }
                if (baseURI.getPath().matches(".*\s+.*")) {
                    return null;
                }
            } catch (URISyntaxException e) {
                return null;
            }
            return baseURI;
        }

        private Page savePage(URI baseURI, Website website) {
            Page page = pageRepository.findByPath(website.getId(), baseURI.getPath());
            if (page != null) {
                return null;
            }
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(baseURI)
                    .GET()
                    .build();
            HttpResponse<String> response = null;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                HttpHeaders headers = response.headers();
                Map<String, List<String>> headersMap = headers.map();
                if (!headersMap.containsKey("content-type")) {
                    return null;
                }
                List<String> contentTypeList = headersMap.get("content-type");
                if (contentTypeList.isEmpty() || !contentTypeList.get(0).matches("text/html.*")) {
                    return null;
                }
                String body = response.body();
                int statusCode = response.statusCode();
                if (statusCode < 200 || statusCode >= 300) {
                    return null;
                }
                page = new Page();
                page.setCode(statusCode);
                page.setPath(baseURI.getPath());
                page.setContent(body);
                pageRepository.save(page);
                page.setSite(website);
                pageRepository.save(page);
                website.setStatusTime(LocalDateTime.now());
                siteRepository.save(website);
                return page;
            } catch (IOException | InterruptedException e) {
                return null;
            }
        }

        private Set<String> getHrefAddresses(URI baseURI, Document document) {
            Set<String> inspectSet = new HashSet<>();
            Elements aElements = document.select("a");
            for (Element aElement : aElements) {
                String href = aElement.attr("href");
                URI resolvedURI;
                URI normalizedURI;
                try {
                    resolvedURI = baseURI.resolve(href);
                    normalizedURI = resolvedURI.normalize();
                    if (!normalizedURI.getScheme().matches("https?")) {
                        continue;
                    }
                    if (!normalizedURI.getHost().equals(baseURI.getHost())) {
                        continue;
                    }
                    String inspectAddress = normalizedURI.getScheme() + "://" + normalizedURI.getHost() + normalizedURI.getPath();
                    inspectSet.add(inspectAddress);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return inspectSet;
        }

        private void saveLemmaMap(HashMap<String, Integer> addLemmaMap, int websiteId, int pageId) {
            Website website = siteRepository.findById(websiteId).orElse(null);
            if (website == null) {
                System.out.println("website == null");
                return;
            }
            Page page = pageRepository.findById(pageId).orElse(null);
            if (page == null) {
                System.out.println("page == null");
                return;
            }
            List<Lemma> lemmasList = lemmaRepository.getLemmasListByWebsiteId(addLemmaMap.keySet(), websiteId);
            HashMap<String, Lemma> lemmasMap = new HashMap<>();
            lemmasList.stream().forEach(e -> {
                lemmasMap.put(e.getLemma(), e);
            });

            addLemmaMap.keySet().stream().forEach(e -> {
                if (e == null || e.isEmpty() || stopRunning.get()) {
                    return;
                }
                Lemma lemma;
                if (lemmasMap.containsKey(e)) {
                    lemma = lemmasMap.get(e);
                    lemma.setFrequency(lemma.getFrequency() + 1);
                    lemmaRepository.save(lemma);
                } else {
                    lemma = new Lemma();
                    lemma.setLemma(e);
                    lemma.setFrequency(1);
                    lemmaRepository.save(lemma);
                    lemma.setSite(website);
                    lemmaRepository.save(lemma);
                }
                Index index = indexRepository.findByLemmaAndPage(lemma.getId(), page.getId());
                if (index != null) {
                    index.setRank(index.getRank() + addLemmaMap.get(e));
                    indexRepository.save(index);
                } else {
                    index = new Index();
                    index.setRank(addLemmaMap.get(e));
                    indexRepository.save(index);
                    index.setLemma(lemma);
                    index.setPage(page);
                    indexRepository.save(index);
                }
            });
        }
    }

    public IndexResponse indexPage(String pageUrl) {
        pageUrl = pageUrl.replaceFirst("url=", "");
        if (pageUrl.isEmpty()) {
            return new IndexErrorResponse(false, "Адрес страницы не указан");
        }

        pageUrl = pageUrl.replaceAll("%3A", ":").replaceAll("%2F", "/");
        if (!pageUrl.matches("https?://.+/.*")) {
            return new IndexErrorResponse(false, "Адрес страницы указан некорректно");
        }
        String[] pageUrlParts = pageUrl.split("/");
        if (pageUrlParts.length < 3) {
            return new IndexErrorResponse(false, "Адрес страницы указан некорректно");
        }
        String domainUrl = pageUrlParts[0] + "//" + pageUrlParts[2];
        pageUrl = pageUrl.replaceFirst(domainUrl, "");

        List<Site> sites = siteList.getSites().stream().filter(e -> e.getUrl().equals(domainUrl)).collect(Collectors.toList());
        Site site = null;
        if (sites.isEmpty()) {
            return new IndexErrorResponse(false, "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        site = sites.get(0);
        Website website = siteRepository.findByUrl(site.getUrl());
        if (website != null) {
            if (website.getStatus().equals(StatusType.INDEXING)) {
                return new IndexErrorResponse(false, "Индексация сайта уже запущена");
            }
        }
        (new Thread(new Starter(site, pageUrl, false))).start();

        return new IndexResponse(true);
    }

    public IndexResponse search(String query, String site, String offset, String limit) {
        HashMap<String, Integer> searchLemmasMap = TextAnalyzer.getLemmas(query);
        if (searchLemmasMap.keySet() == null || searchLemmasMap.keySet().isEmpty()) {
            return new IndexErrorResponse(false, "Задан пустой поисковый запрос");
        }
        Map<Page, Float> relativeRelevanceMap = getRelativeRelevanceMap(site, searchLemmasMap);

        long pageOffset;
        long pageLimit;
        try {
            pageOffset = Long.parseLong(offset);
            pageLimit = Long.parseLong(limit);
        } catch (Exception ex) {
            pageOffset = 0;
            pageLimit = 20;
            ex.printStackTrace();
        }

        List<Page> pages = relativeRelevanceMap.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry<Page, Float>::getValue).reversed())
                .skip(pageOffset)
                .limit(pageLimit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        List<SearchItem> searchItemList = pages.stream().map(page -> {
            SearchItem item = new SearchItem();
            item.setSite(page.getSite().getUrl());
            item.setSiteName(page.getSite().getName());
            item.setUri(page.getPath());
            item.setRelevance(relativeRelevanceMap.get(page));
            item.setTitle(TextAnalyzer.getPageTitle(page.getContent()));
            item.setSnippet(TextAnalyzer.getSnippets(page.getContent(), searchLemmasMap.keySet()));
            return item;
        }).collect(Collectors.toList());
        if (searchItemList == null || searchItemList.isEmpty()) {
            return new IndexErrorResponse(false, "Список найденных страниц пуст");
        }

        return new SearchResponse(true, relativeRelevanceMap.size(), searchItemList);
    }

    private Map<Page, Float> getRelativeRelevanceMap(String site, HashMap<String, Integer> searchLemmasMap) {
        Website website = siteRepository.findByUrl(site);
        int websiteId = 0;
        List<Lemma> lemmas;
        if (website != null) {
            websiteId = website.getId();
            lemmas = lemmaRepository.getLemmasListByWebsiteId(searchLemmasMap.keySet(), websiteId);
        } else {
            lemmas = lemmaRepository.getLemmasList(searchLemmasMap.keySet());
        }
        if (frequencyLimit == 0) {
            frequencyLimit = 20;
        }
        lemmas = lemmas.stream().filter(lemma -> lemma.getFrequency() <= frequencyLimit).sorted(Comparator.comparing(Lemma::getFrequency)).collect(Collectors.toList());

        Map<Page, Map<String, Float>> relevanceMap = new HashMap<>();
        int step = 0;
        for (Lemma lemma : lemmas) {
            step++;
            List<Index> indexes = indexRepository.findByLemma(lemma.getId());
            for (Index index : indexes) {
                Page page = index.getPage();
                Map<String, Float> rankMap;
                if (relevanceMap.containsKey(page)) {
                    rankMap = relevanceMap.get(page);
                } else {
                    rankMap = new HashMap<>();
                }
                if (rankMap.containsKey(lemma.getLemma())) {
                    rankMap.put(lemma.getLemma(), rankMap.get(lemma.getLemma()) + index.getRank());
                } else {
                    rankMap.put(lemma.getLemma(), index.getRank());
                }
                relevanceMap.put(page, rankMap);
            }
            System.out.println();
        }

        long lemmasCount = lemmas.stream().map(Lemma::getLemma).distinct().count();
        relevanceMap = relevanceMap.entrySet().stream()
                .filter(entry -> entry.getValue().size() == lemmasCount)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<Page, Float> absoluteRelevanceMap = new HashMap<>();
        relevanceMap.entrySet().stream().forEach(entry -> {
            Float absoluteRelevance = entry.getValue().values().stream().reduce(0f, (sum, value) -> sum + value);
            absoluteRelevanceMap.put(entry.getKey(), absoluteRelevance);
        });

        Float max = absoluteRelevanceMap.values().stream().max(Float::compare).orElse(Float.MAX_VALUE);

        Map<Page, Float> relativeRelevanceMap = new HashMap<>();
        absoluteRelevanceMap.entrySet().stream().forEach(entry -> {
            Float relativeRelevance = entry.getValue() / max;
            relativeRelevanceMap.put(entry.getKey(), relativeRelevance);
        });

        return relativeRelevanceMap;
    }

    public IndexResponse stopIndexing() {
        boolean indexingRunning = false;
        for (Site site : siteList.getSites()) {
            Website website = siteRepository.findByUrl(site.getUrl());
            if (website != null && website.getStatus().equals(StatusType.INDEXING)) {
                indexingRunning = true;
            }
        }
        if (indexingRunning) {
            stop();
            return new IndexResponse(true);
        } else {
            return new IndexErrorResponse(false, "Индексация не запущена");
        }
    }

    private void stop() {
        System.out.println("Остановка задач");
        stopRunning.set(true);
        while (runningTasksVector.size() > 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        for (Site site : siteList.getSites()) {
            Website website = siteRepository.findByUrl(site.getUrl());
            if (website != null && website.getStatus().equals(StatusType.INDEXING)) {
                website.setStatus(StatusType.FAILED);
                website.setStatusTime(LocalDateTime.now());
                website.setLastError("Индексация остановлена пользователем");
                siteRepository.save(website);
            }
        }
    }
}
