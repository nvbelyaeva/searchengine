package searchengine.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IndexErrorResponse extends IndexResponse {
    private String error;
    public IndexErrorResponse(boolean result, String error) {
        super(result);
        this.error = error;
    }
}
