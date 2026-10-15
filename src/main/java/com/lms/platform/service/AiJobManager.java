package com.lms.platform.service;

import com.google.gson.*;
import java.util.Map;
import java.util.concurrent.*;
import java.time.Instant;

/**
 * Async AI job manager.
 * Jobs are submitted, executed in a background thread pool,
 * and results polled by the frontend via jobId.
 *
 * Flow: submit(prompt) → jobId → poll(jobId) → {status, result}
 *
 * No timeout issue: the backend has no HTTP timeout constraint
 * since the long-running API call happens in a background thread,
 * and the frontend polls with short fast requests.
 */
public class AiJobManager {

    private static final AiJobManager INSTANCE = new AiJobManager();
    public static AiJobManager getInstance() { return INSTANCE; }

    // Thread pool for AI generation tasks
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    // Job storage (in-memory, auto-cleanup after 1 hour)
    private final ConcurrentHashMap<String, AiJob> jobs = new ConcurrentHashMap<>();

    // Auto-cleanup scheduler
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    private AiJobManager() {
        // Cleanup old jobs every 10 minutes
        cleaner.scheduleAtFixedRate(this::cleanupOldJobs, 10, 10, TimeUnit.MINUTES);
    }

    /**
     * Submit an AI generation job. Returns immediately with a jobId.
     */
    public String submitJob(String type, String prompt, AiService aiService) {
        String jobId = type + "-" + System.currentTimeMillis() + "-" + Integer.toHexString((int)(Math.random()*0xFFFF));

        AiJob job = new AiJob();
        job.id = jobId;
        job.type = type;
        job.status = "PENDING";
        job.createdAt = Instant.now();
        job.prompt = prompt.length() > 200 ? prompt.substring(0, 200) + "..." : prompt;
        jobs.put(jobId, job);

        executor.submit(() -> {
            try {
                job.status = "RUNNING";
                job.startedAt = Instant.now();

                // Use lightweight plan generator for plan jobs, full generator for courses
                AiService.AiResult result;
                if ("plan".equals(type)) {
                    result = aiService.generatePlan(prompt);
                } else {
                    result = aiService.generate(prompt);
                }

                job.completedAt = Instant.now();
                job.durationMs = job.completedAt.toEpochMilli() - job.startedAt.toEpochMilli();

                if (result.success()) {
                    job.status = "COMPLETED";
                    job.result = result.content();
                    job.message = result.message();
                } else {
                    job.status = "FAILED";
                    job.message = result.message();
                }
            } catch (Exception e) {
                job.status = "FAILED";
                job.message = "Exception: " + e.getMessage();
                job.completedAt = Instant.now();
                e.printStackTrace(System.err);
            }
        });

        System.out.println("[AI-Jobs] Submitted: " + jobId + " (" + type + ")");
        return jobId;
    }

    /**
     * Poll job status. Returns current state.
     */
    public AiJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    /**
     * List all active jobs (for admin monitoring).
     */
    public Map<String, AiJob> getAllJobs() {
        return jobs;
    }

    /**
     * Cancel a running job (best effort).
     */
    public boolean cancelJob(String jobId) {
        AiJob job = jobs.get(jobId);
        if (job != null && ("PENDING".equals(job.status) || "RUNNING".equals(job.status))) {
            job.status = "CANCELLED";
            job.completedAt = Instant.now();
            return true;
        }
        return false;
    }

    private void cleanupOldJobs() {
        long cutoff = Instant.now().minusSeconds(3600).toEpochMilli(); // 1 hour
        jobs.entrySet().removeIf(e -> {
            AiJob job = e.getValue();
            return job.createdAt.toEpochMilli() < cutoff &&
                   ("COMPLETED".equals(job.status) || "FAILED".equals(job.status) || "CANCELLED".equals(job.status));
        });
    }

    // ═══ Job model ═══

    public static class AiJob {
        public String id;
        public String type;       // "plan" or "course"
        public String status;     // PENDING → RUNNING → COMPLETED / FAILED / CANCELLED
        public String prompt;     // truncated prompt (for display)
        public String result;     // generated content (HTML or JSON)
        public String message;    // usage info or error message
        public Instant createdAt;
        public Instant startedAt;
        public Instant completedAt;
        public long durationMs;

        public JsonObject toJson() {
            JsonObject j = new JsonObject();
            j.addProperty("jobId", id);
            j.addProperty("type", type);
            j.addProperty("status", status);
            j.addProperty("message", message != null ? message : "");
            j.addProperty("durationMs", durationMs);
            if (createdAt != null) j.addProperty("createdAt", createdAt.toString());
            if ("COMPLETED".equals(status) && result != null) {
                j.addProperty("result", result);
            }
            // Progress indicator
            if ("RUNNING".equals(status) && startedAt != null) {
                j.addProperty("elapsedMs", Instant.now().toEpochMilli() - startedAt.toEpochMilli());
            }
            return j;
        }
    }
}
