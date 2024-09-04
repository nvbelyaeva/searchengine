package searchengine.services;

import searchengine.dto.IndexResponse;

public interface IndexingService {
    IndexResponse stopIndexing();
    IndexResponse startIndexing();
    IndexResponse indexPage(String path);
    IndexResponse search(String query, String site, String offset, String limit);
}
