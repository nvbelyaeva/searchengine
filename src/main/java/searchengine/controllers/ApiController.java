package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.IndexResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }
    @GetMapping("/startIndexing")
    public ResponseEntity<IndexResponse> startIndexing() {
        return ResponseEntity.ok(indexingService.startIndexing());
    }
    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexResponse> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }
    @PostMapping("/indexPage")
    public ResponseEntity<IndexResponse> indexPage(@RequestBody String url) {
        return ResponseEntity.ok(indexingService.indexPage(url));
    }
    @GetMapping("/search")
    public ResponseEntity<IndexResponse> search(@RequestParam String query, @RequestParam (defaultValue = "", required = false) String site, @RequestParam (defaultValue = "0") String offset, @RequestParam (defaultValue = "20") String limit){
        return ResponseEntity.ok(indexingService.search(query, site, offset, limit));
    }

}
