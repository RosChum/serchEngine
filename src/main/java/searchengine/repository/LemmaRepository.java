package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {


    @Transactional
    @Query("UPDATE Lemma l set l.frequency = l.frequency + 1 WHERE l.lemma = :lemma AND l.site = :site")
    @Modifying(clearAutomatically = true)
    void updateFrequency(@Param("lemma") String name, @Param("site") Site site);

    List <Lemma> findByLemmaOrderByFrequencyAsc(String text);

    boolean existsLemmaByLemma(String text);

    List<Lemma> findBySite(Site site);

    @Query("SELECT l FROM Lemma as l WHERE l.lemma = :lemma AND l.site = :site")
    Lemma findByLemmaAndSite(@Param("lemma") String lemma, @Param("site") Site site);

    boolean existsLemmaByLemmaAndSite(String lemma,Site site);

}
