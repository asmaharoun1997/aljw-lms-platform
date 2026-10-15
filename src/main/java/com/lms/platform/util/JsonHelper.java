package com.lms.platform.util;
import com.google.gson.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.stream.Collectors;
public class JsonHelper {
    private static final Gson GSON = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create();
    public static Gson gson() { return GSON; }
    public static void json(HttpServletResponse r) { r.setContentType("application/json;charset=UTF-8"); r.setHeader("Cache-Control","no-cache"); }
    public static void ok(HttpServletResponse r, Object data) throws IOException { json(r); r.getWriter().write(GSON.toJson(data)); }
    public static void err(HttpServletResponse r, int status, String msg) throws IOException {
        json(r); r.setStatus(status);
        JsonObject j = new JsonObject(); j.addProperty("error",true); j.addProperty("message",msg);
        r.getWriter().write(GSON.toJson(j));
    }
    public static JsonObject success() { JsonObject j = new JsonObject(); j.addProperty("success",true); return j; }
    public static JsonObject readBody(HttpServletRequest req) throws IOException {
        try (BufferedReader br = req.getReader()) {
            return JsonParser.parseString(br.lines().collect(Collectors.joining("\n"))).getAsJsonObject();
        }
    }
    public static String str(JsonObject j, String key) { return j.has(key) && !j.get(key).isJsonNull() ? j.get(key).getAsString().trim() : ""; }
    public static int intVal(JsonObject j, String key, int def) { return j.has(key) ? j.get(key).getAsInt() : def; }
    public static long longVal(JsonObject j, String key, long def) { return j.has(key) ? j.get(key).getAsLong() : def; }
    public static boolean bool(JsonObject j, String key) { return j.has(key) && j.get(key).getAsBoolean(); }
}
