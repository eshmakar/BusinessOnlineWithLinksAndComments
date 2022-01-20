package ru.eshmakar.business;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.eshmakar.business.domain.ContentNews;
import ru.eshmakar.business.domain.HotNews;
import ru.eshmakar.business.domain.LastNews;
import ru.eshmakar.business.domain.MainNews;
import ru.eshmakar.business.repo.HotNewsRepo;
import ru.eshmakar.business.repo.LastNewsRepo;
import ru.eshmakar.business.repo.MainNewsRepo;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

@Component
public class News {
    String url = "https://m.business-gazeta.ru";
    String userAgent = "Mozilla/5.0 (Linux; Android 4.4.2; Nexus 4 Build/KOT49H) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/34.0.1847.114 Mobile Safari/537.36";
    String regexForNumber = "(.*business-gazeta.ru/)(.*/\\d+)";
    String regexFindPhoto = "(.*)(https://.*\\.jp.?g)(\".*)";
    String replaceTo = "$2";

    @Autowired
    private MainNewsRepo mainNewsRepo;
    @Autowired
    private HotNewsRepo hotNewsRepo;
    @Autowired
    private MainNews mainNews;
    @Autowired
    private LastNewsRepo lastNewsRepo;
    @Autowired
    private ContentNews contentNews;


    public void addMainNews() throws IOException {
        Document document = Jsoup.connect(url).userAgent(userAgent).get();
        String glavnayaTema = "h2.article-news__title";
        String comments = "div.article-news__comments";
        String selectPhoto = "a.article-news__image";
        String linkGlavnaya = document.selectXpath("/html/body/div[3]/article/a").toString();


        mainNews.setPhoto(Objects.requireNonNull(document.selectFirst(selectPhoto)).toString().replaceFirst(regexFindPhoto, replaceTo));
        mainNews.setTitle(Objects.requireNonNull(document.selectFirst(glavnayaTema)).text());
        mainNews.setComments(Objects.requireNonNull(document.selectFirst(comments)).text().substring(12));
        mainNews.setLink(url + linkGlavnaya.substring(9, 24));
        mainNews.setNumbersOfLinks(mainNews.getLink().replaceFirst(regexForNumber, replaceTo).replace("/", "_"));
        mainNewsRepo.save(mainNews);
    }


    public void addHotNews() throws IOException {
        Document document = Jsoup.connect(url).userAgent(userAgent).get();
        String hotNewsTitle = "a.hot-news__title";
        String commentsHotNews = "a.hot-news__comments";
        String selectPhoto = "img.hot-news__img";

        int count = 0;

        ListIterator<Element> elementListIterator = document.select(hotNewsTitle).listIterator();
        ListIterator<Element> commentsIterator = document.select(commentsHotNews).listIterator();
        ListIterator<Element> photosIterator = document.select(selectPhoto).listIterator();

        while (elementListIterator.hasNext() && commentsIterator.hasNext() && photosIterator.hasNext()) {
            HotNews hotNews = new HotNews();
            count++;
            String linkHotNews = document.selectXpath("/html/body/div[3]/section/ul[1]/li[" + count + "]/div/div/a[1]").toString();
            hotNews.setPhoto(photosIterator.next().toString().replaceFirst(regexFindPhoto, replaceTo));
            hotNews.setTitle(elementListIterator.next().text());
            hotNews.setComments(commentsIterator.next().text());

            hotNews.setLink(url + linkHotNews.substring(9, 21));
            hotNews.setNumbersOfLinks(hotNews.getLink().replaceFirst(regexForNumber, replaceTo).replace("/", "_"));
            hotNewsRepo.save(hotNews);

        }
    }

    public void addLastNews() throws IOException {
        Document document = Jsoup.connect(url).userAgent(userAgent).get();
        int count = 0;
        String time = "time.last-news__time";
        String title = "a.last-news__link";
        String comment = "a.last-news__comments";

        ListIterator<Element> times = document.select(time).listIterator();
        ListIterator<Element> titles = document.select(title).listIterator();
        ListIterator<Element> comments = document.select(comment).listIterator();

        while (times.hasNext() && titles.hasNext() && comments.hasNext()) {
            LastNews lastNews = new LastNews();
            count++;
            String links = document.selectXpath("/html/body/div[3]/section/ul[2]/li["+count+"]/div/a[1]").toString();

            lastNews.setTime(times.next().text());
            lastNews.setTitle(titles.next().text());
            lastNews.setComments(Integer.valueOf(comments.next().text()));
            lastNews.setLink(url + links.substring(9, 21));
            lastNews.setNumbersOfLinks(lastNews.getLink().replaceFirst(regexForNumber, replaceTo).replace("/", "_"));

            lastNewsRepo.save(lastNews);

        }
    }

    public void getContent(String urlContent) {
        String url = "https://m.business-gazeta.ru/" + urlContent.replace("_", "/");

        List<String> telo = new LinkedList<>();
        String zagol = "h1.article__h1";
        String getCommentsCount = "span.article__more-count";


        Document doc = null;
        try {
            doc = Jsoup.connect(url).get();
            contentNews.setCommentsCount(doc.select(getCommentsCount).text());
            contentNews.setZagolovok(doc.select(zagol).text());
        } catch (IOException ignored) {
        }

        String toPass1 = "Подписывайтесь и читайте";
        String toPass2 = "Регистрируясь, вы соглашаетесь";

        String videoLinkStart = "https://vk.com/video?z=video";
        String findVideoIdHash = "(.*oid=)(-\\d+)(.*;id=)(\\d+)(.*)";
        String replaceVideo = "$2_$4";

        if (doc != null) {
            for (Element element : doc.select("p")) {
                if (element != null) {
//                    System.out.println(element);
                    if (element.toString().contains("jpg") || element.toString().contains("jpeg")) {
                        String photo = element.toString().replaceAll("\n", "").replaceAll(regexFindPhoto, replaceTo);
                        System.out.println(photo);
                        telo.add(photo);
                    }

                    if (element.toString().contains("<iframe")) {
                        String video = element.toString().replaceAll(findVideoIdHash, replaceVideo);
                        telo.add(videoLinkStart + video);
                    }

                    String text2 = element.text();
                    if (text2.startsWith(toPass1) || text2.startsWith(toPass2)) continue;

                    telo.add(text2);
                }
            }
        }
        contentNews.setTelo(telo);
    }

    public void getComments(String urlContent) throws IOException {
        String url = "https://m.business-gazeta.ru/" + urlContent.replace("_", "/");
        BufferedWriter writer = new BufferedWriter(new FileWriter("Z://comments.ftlh"));

        Document document = null;
        try {
            document = Jsoup.connect(url).get();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String divs = "<div>\n</div>";
        String popovers = " <div class=\"popover popover_favorite\"></div>";
        String bookmarks = "<span class=\"comments-comment__image\"><img class=\"comments-comment__img lazyload\" data-src=\"/img/icons/anonimus.svg\" alt=\"\"></span>";
        String plus = "(.*)( \\d+ )(</span></span>)";

        if (document != null) {
            Elements selects = document.select("ul.comments-list");
            for (Element element : selects) {
                element.select("a").remove();
                element.select("div.popover__wrap").remove();
                element.select("div.comments-comment__etc").remove();
                element.select("div.show_full").remove();
            }

            writer.write("<#import \"parts/common.ftlh\" as c>\n" +
                    "<@c.page>");

            writer.write(selects.html()
                    .replace(divs, "")
                    .replace(popovers, "")
                    .replace(bookmarks, "")
                    .replaceAll(plus, "$2")
                    .replace("\n", "")
                    .replaceAll(" {2,}", " ")
                    .replace("<div class=\"comments-comment__bookmarks\"> </div>", "")
                    .replace("<span class=\"comments-comment__image\"><img class=\"comments-comment__img lazyload\" data-src=\"https://beta-cdn.business-online.ru/img/icons/anonimus.svg\" alt=\"\"></span>", "")
                    .replace("<div class=\"comments-comment__avatar\"> </div>", "")
                    .replaceAll("(<div class=\"comments-comment__rating\"> <div class=\"voting\" data-rating=\"\\d+\" data-comment-id=\"\\d+\" data-article-id=\"\\d+\">)( \\d+ )(</div> </div>)", "<div>$2</div>")
                    .replace(" class=\"comments-comment comments-comment_bad\"", "")
                    .replace(" <div class=\"comments-comment__author\"> <span class=\"comments-comment__name\">Анонимно</span> </div>", "<b>Анонимно</b>")


            );

            writer.write("</@c.page>");
            writer.close();

        } else {
            System.out.println("document is null");
        }
    }
}