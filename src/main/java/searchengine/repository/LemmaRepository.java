package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Site;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {


    @Transactional
    @Query("UPDATE Lemma l set l.frequency = l.frequency + 1 WHERE l.lemma = :lemma")
    @Modifying(clearAutomatically = true)
    void updateFrequency(@Param("lemma") String name);

    Lemma findByLemma(String lemma);

    @Query("SELECT l FROM Lemma as l WHERE l.lemma = :lemma AND l.site = :site")
    Lemma findByLemmaAndSite(@Param("lemma") String lemma, @Param("site") Site site);

    boolean existsLemmaByLemmaAndSite(String lemma,Site site);

}
