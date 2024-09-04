package searchengine.dto.search;

import lombok.Getter;
import lombok.Setter;
import searchengine.dto.IndexResponse;

import java.util.List;

@Getter
@Setter
public class SearchResponse extends IndexResponse {
    private int count;
    private List<SearchItem> data;

    public SearchResponse(boolean result, int count, List<SearchItem> data) {
        super(result);
        this.count = count;
        this.data = data;
    }
}
