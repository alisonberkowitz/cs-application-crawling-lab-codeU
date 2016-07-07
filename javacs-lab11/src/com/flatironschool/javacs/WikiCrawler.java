package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;
	
	// the index where the results go
	private JedisIndex index;
	
	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();
	
	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */
	public int queueSize() {
		return queue.size();	
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b 
	 * 
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {
        String url = queue.poll();
        System.out.println("Crawling " + url);
        if (testing) {
        	Elements paragraphs = wf.readWikipedia(url);
			index.indexPage(url, paragraphs);
			queueInternalLinks(paragraphs, testing);
        }
        else {
        	if (index.isIndexed(url)) {
        		return null;
        	}
        	else {
        		Elements paragraphs = wf.fetchWikipedia(url);
				index.indexPage(url, paragraphs);
				queueInternalLinks(paragraphs, testing);
        	}
        }
        return url;
	}
	
	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs, boolean testing) {
        int i = 0;
		while (i<paragraphs.size()) {
			Element firstPara = paragraphs.get(i);
			Elements links = firstPara.select("a[href]");
			for (Element element: links) {
				String href = element.attr("href");
				// link is not external, but not to the same page
				if (href.startsWith("/wiki")) {
					String absHref = element.attr("abs:href");
					if (testing) {
						absHref = "https://en.wikipedia.org" + href;
					}
					queue.offer(absHref);
				}
			}
			i++;
		}
	}

	private static boolean italicized(Element element) {
    	Elements parents = element.parents();
		boolean italics = false;
		for (Element parent: parents) {
			if (parent.tagName() == "i" || parent.tagName() == "em") {
				italics = true;
			}
		}
		return italics;
    }

    // checks if link is parenthesized in element
    private static boolean parenthesized(Element element, Element link) {
    	// if 0, parentheses are closed
		int parentheses = 0;
		String text = element.text();
		int linkIndex = text.indexOf(link.text());
		for (int i=0; i<linkIndex; i++) {
			if (text.charAt(i)=='(') {
				parentheses++;
			} else if (text.charAt(i)==')') {
				parentheses--;
			}
		}
		return parentheses > 0;
    }

	public static void main(String[] args) throws IOException {
		
		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis); 
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);
		
		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs, false);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);
		} while (res == null);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}
