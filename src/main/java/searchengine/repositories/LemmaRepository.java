package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

import java.util.List;
import java.util.Set;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
//    @Query(value = "SELECT * FROM lemma WHERE lemma = :lemma LIMIT 1", nativeQuery = true)
//    Lemma findByLemma(String lemma);
    @Query(value = "SELECT DISTINCT * FROM lemma WHERE site_id = :websiteId AND lemma IN :lemmas", nativeQuery = true)
    List<Lemma> getLemmasListByWebsiteId(Set<String> lemmas, int websiteId);
    @Query(value = "SELECT DISTINCT * FROM lemma WHERE lemma IN :lemmas", nativeQuery = true)
    List<Lemma> getLemmasList(Set<String> lemmas);
    @Query(value = "SELECT COUNT(id) FROM search_engine.lemma WHERE site_id = :site_id GROUP BY site_id;", nativeQuery = true)
    Integer getLemmaCountBySiteId(int site_id);
}
