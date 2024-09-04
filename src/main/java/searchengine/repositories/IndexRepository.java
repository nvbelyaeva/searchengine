package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    @Query(value = "SELECT * FROM lemmaindex WHERE lemma_id = :lemmaId AND page_id = :pageId LIMIT 1", nativeQuery = true)
    Index findByLemmaAndPage(int lemmaId, int pageId);
    @Query(value = "SELECT * FROM lemmaindex WHERE lemma_id = :lemmaId", nativeQuery = true)
    List<Index> findByLemma(int lemmaId);
    @Query(value = "SELECT * FROM lemmaindex INNER JOIN page ON lemmaindex.page_id = page.id WHERE page.site_id = :websiteId", nativeQuery = true)
    List<Index> findByWebsiteId(int websiteId);
}
