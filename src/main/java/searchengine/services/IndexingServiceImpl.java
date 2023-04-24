package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.searchModel.ResultSearch;
import searchengine.model.*;
import searchengine.repository.IndexSearchRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utility.LemmaСonverter;
import searchengine.utility.SiteIndexMap;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
public class IndexingServiceImpl implements IndexingService {


    private SiteRepository siteRepository;
    private PageRepository pageRepository;
    private SitesList sitesList;
    private ForkJoinPool forkJoinPool;
    private ThreadPoolExecutor threadPoolExecutor;
    private LemmaRepository lemmaRepository;
    private IndexSearchRepository indexSearchRepository;
    private ResultSearch resultSearch;


    public IndexingServiceImpl(SiteRepository siteRepository, PageRepository pageRepository, SitesList sitesList,
                               LemmaRepository lemmaRepository, IndexSearchRepository indexSearchRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sitesList = sitesList;
        this.lemmaRepository = lemmaRepository;
        this.indexSearchRepository = indexSearchRepository;

    }

    @Override
    public void startIndexing() {
        threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(sitesList.getSites().size());
        sitesList.getSites().forEach(siteFromAppProperties -> {
            siteFromAppProperties.setUrl(siteFromAppProperties.getUrl().replace("www.", ""));
            if (siteRepository.existsByUrl(siteFromAppProperties.getUrl())) {
                siteRepository.delete(siteRepository.findByUrl(siteFromAppProperties.getUrl()));
            }
            Site site = new Site();
            site.setName(siteFromAppProperties.getName());
            site.setUrl(siteFromAppProperties.getUrl());
            site.setStatus(IndexingStatus.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
            siteRepository.save(site);

            threadPoolExecutor.submit(() -> walkAndIndexSite(site.getUrl(), siteRepository,
                    pageRepository, site, lemmaRepository, indexSearchRepository));

        });

        threadPoolExecutor.shutdown();
        threadPoolExecutor.getQueue().clear();

    }

    @Override
    public void stopIndexing() {
        if (statusIndexing()) {
            threadPoolExecutor.shutdownNow();
            SiteIndexMap.stop = true;
            ForkJoinPool.commonPool().shutdownNow();

        }

    }

    @Override
    public boolean statusIndexing() {
        if (threadPoolExecutor == null || threadPoolExecutor.getActiveCount() == 0) {
            return false;
        } else {
            return true;
        }

    }

    @Override
    public List<Site> getListSiteIndexing() {
        return new ArrayList<>(siteRepository.findAll());
    }

    @Override
    public void indexPage(String url) {
        Site site = new Site();

        siteRepository.delete(siteRepository.findByUrl(url));

        searchengine.config.Site siteFromConfig = sitesList.getSites().stream()
                .filter(site1 -> site1.getUrl().contains(url.substring(url.indexOf("//"), url.lastIndexOf(".")))).findFirst().get();
        System.out.println(siteFromConfig.getUrl());
        site.setName(siteFromConfig.getName());
        site.setUrl(siteFromConfig.getUrl());
        site.setStatus(IndexingStatus.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(null);

        siteRepository.save(site);

        walkAndIndexSite(site.getUrl(), siteRepository, pageRepository, site, lemmaRepository, indexSearchRepository);

    }

    @Override
    public ResultSearch searchPage(String query, String site) {
        ResultSearch resultSearch = new ResultSearch();
        List<Lemma> foundLemmaListFromQuery = new ArrayList<>();
        List<IndexSearch> foundListIndexSearchByFirstLemma;
        Set<IndexSearch> foundAllIndexFromQuery = new HashSet<>();
        LemmaСonverter lemmaСonverter = new LemmaСonverter();
        try {
            Set<String> queryLemmas = lemmaСonverter.convertTextToLemmas(query).keySet();
            for (String lemmas : queryLemmas) {
                if (site == null || site.isEmpty()) {
                    foundLemmaListFromQuery.addAll(lemmaRepository.findByLemmaOrderByFrequencyAsc(lemmas));
                } else {
                    foundLemmaListFromQuery.addAll(lemmaRepository.findByLemmaAndSite(lemmas, site));
                }
            }

            int countPage = pageRepository.findAll().size();
            List<Lemma> sortedFoundLemmaListFromQuery = foundLemmaListFromQuery.stream().filter(lemma -> lemma.getFrequency() < countPage * 0.37)
                    .sorted(Comparator.comparing(Lemma::getFrequency)).toList();

            if (sortedFoundLemmaListFromQuery.size() > 0) {
                foundListIndexSearchByFirstLemma = sortedFoundLemmaListFromQuery.stream().flatMap(s -> s.getIndexSearches().stream()
                        .filter(f -> f.getLemma().getLemma().equals(sortedFoundLemmaListFromQuery.get(0).getLemma()))
                        .flatMap(f -> f.getPage().getIndexSearches().stream())).collect(Collectors.toList()); //находим все страницы по первой лемме, получаем страницы, получаем леммы страниц

                for (int i = 0; i < sortedFoundLemmaListFromQuery.size(); i++) {
                    int finalI = i;
                    foundAllIndexFromQuery = foundListIndexSearchByFirstLemma.stream()
                            .filter(f -> f.getLemma().equals(sortedFoundLemmaListFromQuery.get(finalI))).collect(Collectors.toSet()); //кривой поиск страниц

                }


            } else {
                return resultSearch;
            }


            sortedFoundLemmaListFromQuery.forEach(f -> System.out.println(f.getId() + "   " + f.getLemma() + "    " + f.getFrequency()));

            foundAllIndexFromQuery.forEach(f -> System.out.println(f.getPage().getSite().getUrl() + f.getPage().getPath()));


        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;

    }


    private void walkAndIndexSite(String urlSite, SiteRepository siteRepository, PageRepository pageRepository, Site site,
                                  LemmaRepository lemmaRepository, IndexSearchRepository indexSearchRepository) {
        SiteIndexMap.stop = false;
        SiteIndexMap siteIndexMap = new SiteIndexMap(urlSite, siteRepository, pageRepository, site, lemmaRepository, indexSearchRepository);
        forkJoinPool = new ForkJoinPool(10);
        Site site1 = forkJoinPool.invoke(siteIndexMap);
        if (site1.getStatus() != IndexingStatus.FAILED) {
            siteRepository.updateStatus(site.getName(), IndexingStatus.INDEXED, null, LocalDateTime.now());
        }
        if (site1.getStatus() != IndexingStatus.FAILED && site1.getStatus() != IndexingStatus.INDEXED && SiteIndexMap.stop) {
            siteRepository.updateStatus(site.getName(), IndexingStatus.FAILED, "Индексация остановлена пользователем", LocalDateTime.now());
        }
        forkJoinPool.shutdown();
    }

    private List<Page> searchMatches(List<Lemma> sortedFoundLemmaListFromQuery, List<IndexSearch> foundListIndexSearchByFirstLemma, int countIteration) {

        List<Page> pagesMatchesLemmas = new ArrayList<>();


        return pagesMatchesLemmas;

    }


}
