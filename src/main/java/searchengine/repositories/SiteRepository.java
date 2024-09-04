package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Website;

@Repository
public interface SiteRepository extends JpaRepository<Website, Integer> {
    @Query(value = "SELECT * from site where url = :url LIMIT 1", nativeQuery = true)
    Website findByUrl(String url);
}
