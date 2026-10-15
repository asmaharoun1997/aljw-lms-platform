package com.lms.platform.service;

import com.lms.platform.dao.CourseDAO;
import com.lms.platform.model.Course;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Content indexing service.
 * Extracts text from HTML files in deployed bundles and indexes it for full-text search.
 * Indexes: title, description, tags, category + extracted text from HTML files.
 */
public class SearchService {

    private final CourseDAO courseDAO = new CourseDAO();

    /**
     * Index a course/document after deployment.
     * Extracts text from all HTML files in the bundle and stores in search_index.
     */
    public void indexCourse(Course course) {
        try {
            Map<String, String> fields = new LinkedHashMap<>();

            // Meta fields (from bundle.xml)
            fields.put("title", course.getTitle());
            fields.put("description", course.getDescription());
            fields.put("tags", course.getTags());
            fields.put("category", course.getCategory());
            fields.put("level", course.getLevel());
            fields.put("author", course.getAuthorName());

            // Extract text from HTML files in the bundle
            if (course.getDeployedPath() != null) {
                Path bundlePath = Path.of(course.getDeployedPath());
                if (Files.isDirectory(bundlePath)) {
                    StringBuilder allText = new StringBuilder();
                    try (var stream = Files.walk(bundlePath)) {
                        stream.filter(p -> p.toString().endsWith(".html") || p.toString().endsWith(".htm"))
                              .forEach(htmlFile -> {
                                  try {
                                      String html = Files.readString(htmlFile);
                                      String text = extractText(html);
                                      if (!text.isBlank()) allText.append(text).append(" ");
                                  } catch (IOException ignored) {}
                              });
                    }
                    if (!allText.isEmpty()) {
                        // Truncate to 500KB max for indexing
                        String bodyText = allText.toString();
                        if (bodyText.length() > 500_000) bodyText = bodyText.substring(0, 500_000);
                        fields.put("body", bodyText);
                    }
                }
            }

            courseDAO.indexContent(course.getId(), fields);
            System.out.println("[LMS-Search] Indexed: " + course.getTitle() +
                    " (" + fields.size() + " fields, " +
                    fields.values().stream().mapToInt(String::length).sum() + " chars)");

        } catch (Exception e) {
            System.err.println("[LMS-Search] Index error for course " + course.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Extracts visible text from HTML, stripping tags, scripts, styles.
     * Also extracts meta tag content attributes.
     */
    static String extractText(String html) {
        if (html == null || html.isBlank()) return "";

        StringBuilder sb = new StringBuilder();

        // Extract <meta> content attributes
        Matcher metaMatcher = Pattern.compile(
                "<meta[^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
        while (metaMatcher.find()) {
            sb.append(metaMatcher.group(1)).append(" ");
        }

        // Extract <title>
        Matcher titleMatcher = Pattern.compile(
                "<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE).matcher(html);
        if (titleMatcher.find()) {
            sb.append(titleMatcher.group(1)).append(" ");
        }

        // Remove <script>, <style>, <svg>, <code> blocks
        String cleaned = html.replaceAll("(?si)<(script|style|svg|noscript)[^>]*>.*?</\\1>", " ");

        // Remove all HTML tags
        cleaned = cleaned.replaceAll("<[^>]+>", " ");

        // Decode common HTML entities
        cleaned = cleaned.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                         .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");

        // Collapse whitespace
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        sb.append(cleaned);
        return sb.toString();
    }

    /**
     * Re-index all deployed courses/documents.
     * Called from admin or on startup if needed.
     */
    public int reindexAll() {
        int count = 0;
        try {
            List<Course> all = courseDAO.findAll();
            for (Course c : all) {
                if (c.getDeployedPath() != null) {
                    indexCourse(c);
                    count++;
                }
            }
        } catch (Exception e) {
            System.err.println("[LMS-Search] Reindex error: " + e.getMessage());
        }
        return count;
    }
}
