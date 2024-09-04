package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    @Query(value = "SELECT * FROM page WHERE site_id = :site_id AND path = :path LIMIT 1", nativeQuery = true)
    Page findByPath(int site_id, String path);

    @Query(value = "SELECT * FROM page WHERE site_id = :site_id AND path IN :pathList", nativeQuery = true)
    List<Page> findByPathList(int site_id, List<String> pathList);

    @Query(value = "SELECT COUNT(id) FROM search_engine.page WHERE site_id = :site_id GROUP BY site_id;", nativeQuery = true)
    Integer getPageCountBySiteId(int site_id);
}
