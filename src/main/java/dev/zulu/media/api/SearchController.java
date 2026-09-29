package dev.zulu.media.api;

import dev.zulu.media.search.SearchResponse;
import dev.zulu.media.search.YtDlpSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plugins/zulu-media/v1")
public final class SearchController {

    private final YtDlpSearchService searchService;

    public SearchController(YtDlpSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public SearchResponse search(
        @RequestParam("q") String query,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return searchService.search(query, limit);
    }
}
