package com.lms.platform.service;

import com.google.gson.*;
import com.lms.platform.dao.SettingDAO;
import com.lms.platform.util.AppConfig;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/**
 * Proxy service for Anthropic Claude API.
 * The API key never leaves the server — the frontend sends course specs,
 * the backend calls Claude and returns the generated HTML.
 */
public class AiService {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    private static final int MAX_TOKENS = 16384;

    private final SettingDAO settingDAO = new SettingDAO();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Callback interface for streaming text deltas.
     */
    public interface StreamCallback {
        void onDelta(String text) throws Exception;
        void onComplete(String usage) throws Exception;
        void onError(String error) throws Exception;
    }

    /**
     * Stream course generation — sends text deltas via callback as they arrive.
     * Uses Anthropic streaming API (SSE). No timeout issue since data flows continuously.
     */
    public void generateStream(String userPrompt, StreamCallback callback) {
        String apiKey = getApiKey();
        if (apiKey.isBlank()) {
            try { callback.onError("Cle API Anthropic non configuree."); } catch (Exception ignored) {}
            return;
        }

        try {
            String systemPrompt = getSystemPrompt();
            String model = getModel();

            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", MAX_TOKENS);
            body.addProperty("stream", true); // Enable streaming
            body.addProperty("system", systemPrompt);

            JsonArray messages = new JsonArray();
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", userPrompt);
            messages.add(msg);
            body.add("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ANTHROPIC_API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            // Stream the response line by line
            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String errBody = new String(response.body().readAllBytes());
                try {
                    JsonObject err = JsonParser.parseString(errBody).getAsJsonObject();
                    String errMsg = err.has("error") ? err.getAsJsonObject("error").get("message").getAsString() : errBody;
                    callback.onError("API Anthropic " + response.statusCode() + ": " + errMsg);
                } catch (Exception e) {
                    callback.onError("API Anthropic HTTP " + response.statusCode());
                }
                return;
            }

            // Parse SSE stream
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8));

            String line;
            String inputTokens = "", outputTokens = "";

            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if (data.equals("[DONE]")) break;

                try {
                    JsonObject event = JsonParser.parseString(data).getAsJsonObject();
                    String type = event.has("type") ? event.get("type").getAsString() : "";

                    switch (type) {
                        case "content_block_delta" -> {
                            JsonObject delta = event.getAsJsonObject("delta");
                            if (delta != null && "text_delta".equals(delta.get("type").getAsString())) {
                                callback.onDelta(delta.get("text").getAsString());
                            }
                        }
                        case "message_delta" -> {
                            // Final usage stats
                            if (event.has("usage")) {
                                JsonObject u = event.getAsJsonObject("usage");
                                if (u.has("output_tokens")) outputTokens = u.get("output_tokens").getAsString();
                            }
                        }
                        case "message_start" -> {
                            if (event.has("message")) {
                                JsonObject m = event.getAsJsonObject("message");
                                if (m.has("usage")) {
                                    JsonObject u = m.getAsJsonObject("usage");
                                    if (u.has("input_tokens")) inputTokens = u.get("input_tokens").getAsString();
                                }
                            }
                        }
                        case "error" -> {
                            JsonObject err = event.getAsJsonObject("error");
                            callback.onError(err != null ? err.get("message").getAsString() : "Erreur streaming");
                            return;
                        }
                    }
                } catch (Exception e) {
                    // Skip malformed SSE lines
                }
            }

            reader.close();
            String usage = "Tokens: " + inputTokens + " in, " + outputTokens + " out";
            callback.onComplete(usage);

        } catch (Exception e) {
            try { callback.onError("Erreur: " + e.getMessage()); } catch (Exception ignored) {}
        }
    }

    /**
     * Returns the effective system prompt (from DB settings, or default).
     */
    public String getSystemPrompt() {
        try {
            String dbPrompt = settingDAO.get("ai_system_prompt");
            if (dbPrompt != null && !dbPrompt.isBlank()) return dbPrompt;
        } catch (Exception ignored) {}
        return getDefaultSystemPrompt();
    }

    /**
     * Returns the configured model name.
     */
    public String getModel() {
        try {
            String dbModel = settingDAO.get("ai_model");
            if (dbModel != null && !dbModel.isBlank()) return dbModel;
        } catch (Exception ignored) {}
        return AppConfig.get("lms.ai.model", DEFAULT_MODEL);
    }

    /**
     * Returns the API key (from DB settings or config file).
     */
    private String getApiKey() {
        try {
            String dbKey = settingDAO.get("ai_api_key");
            if (dbKey != null && !dbKey.isBlank()) return dbKey;
        } catch (Exception ignored) {}
        return AppConfig.get("lms.ai.api-key", "");
    }

    /**
     * Check if AI features are available (API key configured).
     */
    public boolean isEnabled() {
        return !getApiKey().isBlank();
    }

    /**
     * Generate a structured course plan (JSON only).
     * Uses a lightweight system prompt — NOT the full HTML generation prompt.
     * Validates that the response is complete JSON before returning.
     */
    public AiResult generatePlan(String userPrompt) {
        String apiKey = getApiKey();
        if (apiKey.isBlank()) {
            return new AiResult(false, null, "Cle API Anthropic non configuree.");
        }

        try {
            String model = getModel();
            String systemPrompt = "Tu es un expert en ingénierie pédagogique. " +
                "Tu génères des plans de cours structurés au format JSON. " +
                "Réponds UNIQUEMENT avec du JSON valide, sans texte avant ou après, sans backticks. " +
                "Le JSON doit commencer par { et terminer par }. " +
                "Structure: {\"modules\":[{\"number\":1,\"title\":\"...\",\"slides\":[{\"title\":\"...\",\"type\":\"sc\",\"content_summary\":\"...\"}]}]}";

            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", 4096); // Plan needs much less than 16K
            body.addProperty("system", systemPrompt);

            JsonArray messages = new JsonArray();
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", userPrompt);
            messages.add(msg);
            body.add("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ANTHROPIC_API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                JsonObject errBody = JsonParser.parseString(response.body()).getAsJsonObject();
                String errMsg = errBody.has("error") ?
                        errBody.getAsJsonObject("error").get("message").getAsString() :
                        "HTTP " + response.statusCode();
                return new AiResult(false, null, "API Anthropic: " + errMsg);
            }

            JsonObject respBody = JsonParser.parseString(response.body()).getAsJsonObject();

            // Check stop reason
            String stopReason = respBody.has("stop_reason") ? respBody.get("stop_reason").getAsString() : "unknown";
            System.out.println("[AI-Plan] status=" + response.statusCode() + " stop_reason=" + stopReason);

            // Extract text
            JsonArray content = respBody.getAsJsonArray("content");
            StringBuilder text = new StringBuilder();
            for (var el : content) {
                JsonObject block = el.getAsJsonObject();
                if ("text".equals(block.get("type").getAsString())) {
                    text.append(block.get("text").getAsString());
                }
            }

            String raw = text.toString().trim();

            // Strip code fences if present
            if (raw.contains("```")) {
                int fenceStart = raw.indexOf("```");
                int contentStart = raw.indexOf('\n', fenceStart);
                if (contentStart > 0) {
                    contentStart++;
                    int fenceEnd = raw.indexOf("```", contentStart);
                    if (fenceEnd > contentStart) raw = raw.substring(contentStart, fenceEnd).trim();
                }
            }

            // Extract JSON: find first { and last }
            int firstBrace = raw.indexOf('{');
            int lastBrace = raw.lastIndexOf('}');
            if (firstBrace < 0 || lastBrace <= firstBrace) {
                return new AiResult(false, null, "Pas de JSON dans la reponse (stop_reason: " + stopReason + ")");
            }
            String json = raw.substring(firstBrace, lastBrace + 1);

            // Validate JSON structure
            try {
                JsonObject plan = JsonParser.parseString(json).getAsJsonObject();
                if (!plan.has("modules") || plan.getAsJsonArray("modules").size() == 0) {
                    return new AiResult(false, null, "JSON valide mais pas de modules");
                }
            } catch (Exception e) {
                // If truncated (stop_reason = max_tokens), the JSON is incomplete
                if ("max_tokens".equals(stopReason)) {
                    return new AiResult(false, null,
                            "Reponse tronquee (max_tokens atteint). Reduisez le nombre de modules demandes.");
                }
                return new AiResult(false, null, "JSON invalide: " + e.getMessage());
            }

            // Usage info
            String usage = "";
            if (respBody.has("usage")) {
                JsonObject u = respBody.getAsJsonObject("usage");
                usage = "Tokens: " + u.get("input_tokens").getAsInt() + " in, " +
                        u.get("output_tokens").getAsInt() + " out (stop: " + stopReason + ")";
            }

            return new AiResult(true, json, usage);

        } catch (Exception e) {
            return new AiResult(false, null, "Erreur: " + e.getMessage());
        }
    }

    /**
     * Generate course content via Claude API.
     * @param userPrompt The formateur's instructions (title, description, modules, etc.)
     * @return The generated HTML content
     */
    public AiResult generate(String userPrompt) {
        String apiKey = getApiKey();
        if (apiKey.isBlank()) {
            return new AiResult(false, null, "Cle API Anthropic non configuree. Contactez l'administrateur.");
        }

        try {
            String systemPrompt = getSystemPrompt();
            String model = getModel();

            // Build request body
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", MAX_TOKENS);
            body.addProperty("system", systemPrompt);

            JsonArray messages = new JsonArray();
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", userPrompt);
            messages.add(msg);
            body.add("messages", messages);

            // Call Anthropic API
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ANTHROPIC_API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .timeout(Duration.ofSeconds(600)) // 10 min — runs in background thread, no HTTP timeout
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                JsonObject errBody = JsonParser.parseString(response.body()).getAsJsonObject();
                String errMsg = errBody.has("error") ?
                        errBody.getAsJsonObject("error").get("message").getAsString() :
                        "HTTP " + response.statusCode();
                return new AiResult(false, null, "Erreur API Anthropic: " + errMsg);
            }

            // Extract text content from response
            JsonObject respBody = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray content = respBody.getAsJsonArray("content");
            StringBuilder text = new StringBuilder();
            for (var el : content) {
                JsonObject block = el.getAsJsonObject();
                if ("text".equals(block.get("type").getAsString())) {
                    text.append(block.get("text").getAsString());
                }
            }

            String html = text.toString();

            // Extract content if wrapped in code fences (```html, ```json, ```, etc.)
            if (html.contains("```")) {
                // Find the first code fence and extract content between fences
                int fenceStart = html.indexOf("```");
                int contentStart = html.indexOf('\n', fenceStart);
                if (contentStart > 0) {
                    contentStart++; // skip the newline
                    int fenceEnd = html.indexOf("```", contentStart);
                    if (fenceEnd > contentStart) {
                        html = html.substring(contentStart, fenceEnd).trim();
                    }
                }
            }

            // Extract usage info + stop reason
            String stopReason = respBody.has("stop_reason") ? respBody.get("stop_reason").getAsString() : "";
            String usage = "";
            if (respBody.has("usage")) {
                JsonObject u = respBody.getAsJsonObject("usage");
                usage = "Tokens: " + u.get("input_tokens").getAsInt() + " in, " +
                        u.get("output_tokens").getAsInt() + " out";
                if ("max_tokens".equals(stopReason)) usage += " ⚠️ TRONQUÉ (max_tokens)";
            }

            return new AiResult(true, html, usage);

        } catch (Exception e) {
            return new AiResult(false, null, "Erreur: " + e.getMessage());
        }
    }

    /**
     * Default system prompt for course generation.
     */
    public static String getDefaultSystemPrompt() {
        return """
Tu es un concepteur pédagogique expert spécialisé dans la création de supports de formation interactifs pour une plateforme LMS d'entreprise. Tu génères des fichiers HTML autonomes, complets, prêts à être déployés comme bundles sur la plateforme.

═══════════════════════════════════════════════════════
MISSION
═══════════════════════════════════════════════════════

Génère un fichier `index.html` COMPLET et AUTONOME contenant :
- Tout le CSS inline (pas de fichier externe sauf Google Fonts)
- Tout le HTML des slides (cover + modules + contenu)
- Tout le JavaScript de navigation
- Le support window.LMS pour l'intégration plateforme

Le fichier doit fonctionner en standalone (ouverture directe dans un navigateur) ET au sein de la plateforme LMS (via window.LMS injecté par le serveur).

═══════════════════════════════════════════════════════
DESIGN SYSTEM — RESPECTER À LA LETTRE
═══════════════════════════════════════════════════════

POLICES (Google Fonts — seule dépendance externe autorisée) :
```html
<link href=\"https://fonts.googleapis.com/css2?family=Space+Mono:wght@400;700&family=Sora:wght@300;400;500;600;700;800&family=Instrument+Sans:wght@400;500;600;700&display=swap\" rel=\"stylesheet\">
```
- Sora : titres (h1, h2, h3, noms de modules, badges)
- Instrument Sans : corps de texte, paragraphes, descriptions
- Space Mono : code, identifiants techniques, numéros de slides, tags

PALETTE DE COULEURS (variables CSS obligatoires) :
```css
:root {
  --bg: #06080d;        /* Fond principal (très sombre) */
  --bg2: #0c1018;       /* Fond sidebar, topbar */
  --bg3: #131926;       /* Fond blocs code, écrans */
  --bg4: #1a2236;       /* Fond alternatif */
  --surface: #161e2e;   /* Fond cartes */
  --surface2: #1e293b;  /* Fond cartes hover */
  --border: #1c2740;    /* Bordures principales */
  --border2: #253350;   /* Bordures hover */
  --text: #e2e8f0;      /* Texte principal (clair) */
  --text2: #94a3b8;     /* Texte secondaire */
  --text3: #64748b;     /* Texte tertiaire */
  --text4: #475569;     /* Texte discret */
  --blue: #4285f4;      /* Google Blue — accent principal */
  --red: #ea4335;       /* Google Red — erreurs, dangers */
  --yellow: #fbbc04;    /* Google Yellow — warnings */
  --green: #34a853;     /* Google Green — succès, tips */
  --cyan: #22d3ee;      /* Cyan — highlights, liens */
  --purple: #a78bfa;    /* Violet — code, refs */
  --pink: #f472b6;      /* Rose — keywords */
  --orange: #fb923c;    /* Orange — valeurs */
  --glow: 0 0 40px rgba(34,211,238,.08); /* Glow effet hover */
  --radius: 12px;
}
```

THÈME : Dark mode exclusivement. Fond quasi-noir, texte clair, accents colorés Google.

═══════════════════════════════════════════════════════
STRUCTURE HTML
═══════════════════════════════════════════════════════

Le fichier HTML suit EXACTEMENT cette structure :

```html
<!DOCTYPE html>
<html lang=\"fr\">
<head>
  <meta charset=\"UTF-8\">
  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
  <title>[TITRE DU COURS]</title>
  <link href=\"[GOOGLE FONTS URL]\" rel=\"stylesheet\">
  <style>
    /* TOUT LE CSS ICI — voir section CSS ci-dessous */
  </style>
</head>
<body>

  <!-- SIDEBAR (sommaire cliquable, généré par JS) -->
  <div class=\"sidebar\" id=\"sb\">
    <div class=\"sb-h\"><h2>📋 Sommaire</h2><button class=\"sb-x\" onclick=\"tog()\">×</button></div>
    <div class=\"sb-b\" id=\"toc\"></div>
  </div>
  <div class=\"overlay\" id=\"ov\" onclick=\"tog()\"></div>

  <!-- TOPBAR (fixe, avec progression) -->
  <div class=\"topbar\">
    <button class=\"tb-m\" onclick=\"tog()\">
      <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M4 6h16M4 12h16M4 18h16\"/></svg>
    </button>
    <div class=\"tb-d\"><i style=\"background:var(--blue)\"></i><i style=\"background:var(--red)\"></i><i style=\"background:var(--yellow)\"></i><i style=\"background:var(--green)\"></i></div>
    <span class=\"tb-t\" id=\"tbT\">[TITRE COURT]</span>
    <span class=\"tb-u\" id=\"tbU\">—</span>
    <span class=\"tb-c\"><b id=\"cur\">1</b><span id=\"tot\"></span></span>
    <div class=\"tb-p\" id=\"prog\"></div>
  </div>

  <!-- DECK DE SLIDES -->
  <div class=\"deck\" id=\"deck\">
    <!-- Chaque slide est un <div class=\"sl [TYPE]\"> -->
    <div class=\"sl cover\" data-title=\"Accueil\" data-group=\"Accueil\">...</div>
    <div class=\"sl divider\" data-title=\"Module 01\" data-group=\"01 · NomModule\">...</div>
    <div class=\"sl sc\" data-title=\"Titre slide\" data-group=\"01 · NomModule\">...</div>
    <!-- ... toutes les slides ... -->
  </div>

  <!-- NAVIGATION (flèches bas-droite) -->
  <div class=\"nav\">
    <button class=\"nb\" id=\"pb\" onclick=\"go(-1)\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M15 18l-6-6 6-6\"/></svg></button>
    <button class=\"nb\" id=\"nb2\" onclick=\"go(1)\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M9 18l6-6-6-6\"/></svg></button>
  </div>
  <div class=\"keys\"><span class=\"key\">← →</span><span class=\"key\">S</span><span style=\"font-size:9px;color:var(--text4)\">sommaire</span></div>

  <script>
    /* TOUT LE JS DE NAVIGATION ICI — voir section JS ci-dessous */
  </script>
</body>
</html>
```

═══════════════════════════════════════════════════════
TYPES DE SLIDES
═══════════════════════════════════════════════════════

Chaque slide est un <div class=\"sl [TYPE]\" data-title=\"...\" data-group=\"...\">

1. COVER (page de titre du cours) — class=\"sl cover\"
   Contient :
   - div.mesh + div.grid-bg + div.orb.o1 + div.orb.o2 (effets de fond)
   - div.cv-b : badge (ex: \"Formation pratique — Labs guidés\")
   - h1.cv-t : titre principal avec spans colorés (.g=blue, .c=red, .p=yellow, .w=white, .k=cyan)
   - p.cv-s : sous-titre
   - div.cv-m : métadonnées (spans avec ::before dot cyan)

2. DIVIDER (séparateur de module) — class=\"sl divider\"
   Contient :
   - div.bn : numéro de module en gros (ex: \"01\") — position absolute, très grand, opacité faible
   - div.dl : label module (ex: \"Module 01\") — Space Mono, cyan, uppercase
   - div.dt : titre du module — Sora, très grand
   - div.dd : description courte du module

3. SC (contenu standard) — class=\"sl sc\"
   Contient :
   - div.hdr : header avec div.tag (label module) + h2 (titre de la slide)
   - p.bt : body text — paragraphes explicatifs
   - Puis les composants de contenu (voir ci-dessous)

═══════════════════════════════════════════════════════
COMPOSANTS DE CONTENU (à utiliser dans les slides SC)
═══════════════════════════════════════════════════════

CARTES (grille) :
```html
<div class=\"grid g2\"> <!-- g2=2 colonnes, g3=3, g4=4 -->
  <div class=\"cd\">
    <div class=\"ci bl\">📦</div> <!-- ci = card icon. Couleurs: bl, gr, yl, rd, cy, pu, pk, or -->
    <h3>Titre</h3>
    <p>Description</p>
  </div>
</div>
```

BLOCS DE CODE :
```html
<div class=\"code\" data-t=\"bash\"> <!-- data-t = label en haut à droite -->
  <span class=\"m\"># Commentaire</span>
  <span class=\"c\">commande</span> argument <span class=\"f\">--flag</span>=<span class=\"s\">valeur</span>
</div>
```
Classes de coloration du code :
- .c = cyan (commandes)
- .f = purple (flags)
- .s = green (strings/valeurs)
- .m = grey italic (commentaires)
- .v = yellow (variables)
- .k = pink (keywords)
- .o = orange (nombres)

BLOCS D'INFORMATION :
```html
<div class=\"ib tip\">   <!-- tip=vert, warn=jaune, danger=rouge, lab=bleu, console=violet -->
  <div class=\"ico\">💡</div>
  <div>
    <strong>Titre</strong>
    <p>Contenu</p>
  </div>
</div>
```

TABLEAUX :
```html
<table class=\"tbl\">
  <thead><tr><th>Col 1</th><th>Col 2</th></tr></thead>
  <tbody>
    <tr><td class=\"sn\">Label fort</td><td>Valeur</td></tr>
  </tbody>
</table>
```
Badges dans les tableaux : <span class=\"pill auto\">Vert</span> <span class=\"pill std\">Bleu</span> <span class=\"pill dep\">Rouge</span> <span class=\"pill new\">Violet</span>

DIAGRAMMES D'ARCHITECTURE :
```html
<div class=\"arch\">
  <div style=\"font-size:10px;color:var(--text4);font-family:'Space Mono'\">TITRE DU DIAGRAMME</div>
  <div class=\"ar\"> <!-- ligne de noeuds -->
    <div class=\"an\">Noeud A<small>détail</small></div>
    <div class=\"aa\">→</div> <!-- flèche -->
    <div class=\"an hi\">Noeud B<small>détail</small></div> <!-- hi = highlight (bordure bleue) -->
  </div>
  <div class=\"al\">Légende</div> <!-- texte centré sous le diagramme -->
</div>
```

MOCKUPS D'ÉCRAN (Console/Interface) :
```html
<div class=\"screen\">
  <div class=\"screen-bar\">
    <div class=\"dots\"><i style=\"background:var(--red)\"></i><i style=\"background:var(--yellow)\"></i><i style=\"background:var(--green)\"></i></div>
    <span class=\"url\">console.cloud.google.com/...</span>
  </div>
  <div class=\"screen-body\">
    <div class=\"nav-path\"><span>Section</span><span class=\"sep\">›</span><span>Page</span></div>
    <div class=\"field\"><div class=\"field-label\">Champ</div><div class=\"field-value\">Valeur</div></div>
  </div>
</div>
```

ÉTAPES NUMÉROTÉES (Labs) :
```html
<div class=\"steps\">
  <div class=\"st\">
    <div>
      <h4>Titre de l'étape</h4>
      <p>Instructions</p>
      <!-- + blocs de code, info blocks, etc. -->
    </div>
  </div>
</div>
```

HEADER DE LAB :
```html
<div class=\"lb-h\">
  <span class=\"lb-b\">🔬 Lab N</span>
  <span class=\"lb-d\">⏱ ~X min</span>
</div>
```

DEUX COLONNES :
```html
<div class=\"two\"> <!-- grid 2 colonnes, responsive -->
  <div>Colonne gauche</div>
  <div>Colonne droite</div>
</div>
```

LIEN QCM (dernière slide) :
```html
<a class=\"qcm-btn\" href=\"quiz.html\">🎯 Démarrer le QCM →</a>
```

═══════════════════════════════════════════════════════
JAVASCRIPT DE NAVIGATION (à copier TEL QUEL)
═══════════════════════════════════════════════════════

```javascript
const S=document.querySelectorAll('.sl'),N=S.length;let ci=0;
const $=id=>document.getElementById(id);
const cur=$('cur'),tot=$('tot'),prog=$('prog'),tbT=$('tbT'),pb=$('pb'),nb=$('nb2'),sb=$('sb'),ov=$('ov'),toc=$('toc');
tot.textContent=' / '+N;
if(window.LMS){$('tbU').textContent=window.LMS.user.displayName;}else{$('tbU').textContent='Mode standalone';}
!function(){let h='',g='';S.forEach((s,i)=>{const gr=s.dataset.group||'';if(gr&&gr!==g&&gr!=='Accueil'&&gr!=='QCM'&&gr!=='Fin'){g=gr;h+=`<div class=\"tg-t\">${gr}</div>`}const t=s.dataset.title||'Slide '+(i+1);const lab=s.dataset.lab?'<span class=\"tt lab\">Lab</span>':'';h+=`<div class=\"tl\" data-i=\"${i}\" onclick=\"jump(${i})\"><span class=\"tn\">${String(i+1).padStart(2,'0')}</span><span>${t}</span>${lab}</div>`});toc.innerHTML=h}();
function upd(){S.forEach((s,i)=>{s.classList.remove('active','exit');if(i===ci)s.classList.add('active');else if(i<ci)s.classList.add('exit')});cur.textContent=ci+1;prog.style.width=(ci/(N-1)*100)+'%';tbT.textContent=S[ci].dataset.title||'';pb.disabled=ci===0;nb.disabled=ci===N-1;document.querySelectorAll('.tl').forEach(l=>{l.classList.toggle('active',+l.dataset.i===ci)});const a=document.querySelector('.tl.active');if(a)a.scrollIntoView({block:'nearest',behavior:'smooth'})}
function go(d){const n=ci+d;if(n>=0&&n<N){ci=n;upd()}}
function jump(i){ci=i;upd();tog(false)}
function tog(f){const o=f!==undefined?f:!sb.classList.contains('open');sb.classList.toggle('open',o);ov.classList.toggle('show',o)}
document.addEventListener('keydown',e=>{if(e.key==='ArrowRight'||e.key==='ArrowDown'){e.preventDefault();go(1)}else if(e.key==='ArrowLeft'||e.key==='ArrowUp'){e.preventDefault();go(-1)}else if(e.key==='s'||e.key==='S'){e.preventDefault();tog()}else if(e.key==='Escape')tog(false)});
let tx=0;document.addEventListener('touchstart',e=>{tx=e.touches[0].clientX});document.addEventListener('touchend',e=>{const dx=e.changedTouches[0].clientX-tx;if(Math.abs(dx)>50){dx<0?go(1):go(-1)}});
upd();
```

IMPORTANT : Ce JS doit être copié TEL QUEL sans modification. Il gère :
- Navigation par flèches clavier (← → ↑ ↓)
- Navigation tactile (swipe gauche/droite)
- Sidebar sommaire (touche S)
- Barre de progression
- Numéro de slide
- Intégration window.LMS (nom utilisateur)

═══════════════════════════════════════════════════════
DATA ATTRIBUTES SUR LES SLIDES
═══════════════════════════════════════════════════════

Chaque <div class=\"sl ...\"> DOIT avoir :
- data-title=\"Titre affiché\" → affiché dans la topbar et le sommaire
- data-group=\"01 · NomModule\" → regroupe les slides dans le sommaire
- data-lab=\"true\" (optionnel) → affiche un badge \"Lab\" dans le sommaire

Valeurs spéciales de data-group exclues du sommaire : \"Accueil\", \"QCM\", \"Fin\"

═══════════════════════════════════════════════════════
RÈGLES PÉDAGOGIQUES
═══════════════════════════════════════════════════════

1. PROGRESSION : du simple au complexe, chaque module construit sur le précédent
2. CONCRET : exemples réels, commandes exécutables, cas d'usage métier
3. VISUEL : au moins 1 diagramme d'architecture par module, tableaux comparatifs, cartes
4. LABS : étapes numérotées avec commandes copiables, résultats attendus, nettoyage
5. DENSITÉ : chaque slide a 1 idée principale, pas de surcharge. Préférer plus de slides courtes
6. COULEUR : utiliser les info blocks (tip=vert pour conseils, warn=jaune pour attention, danger=rouge pour erreurs, console=violet pour navigation UI)
7. CODE : toujours coloriser avec les spans de classe (.c .f .s .m .v .k .o)
8. RESPONSIVE : le CSS est responsive (media queries pour mobile incluses dans le design system)

═══════════════════════════════════════════════════════
STRUCTURE TYPE D'UN COURS
═══════════════════════════════════════════════════════

1. Slide COVER (page de titre)
2. Pour chaque module :
   a. Slide DIVIDER (séparateur avec numéro + titre)
   b. 1-3 slides SC (contenu théorique avec diagrammes, tableaux, cartes)
   c. 1 slide SC lab (exercice pratique guidé pas à pas)
3. Slide COVER finale (lien vers QCM si applicable, ou résumé)

Nombre total de slides recommandé : 15 à 30 selon la durée du cours.

═══════════════════════════════════════════════════════
FORMAT DE SORTIE
═══════════════════════════════════════════════════════

Génère UNIQUEMENT le code HTML complet, de <!DOCTYPE html> à </html>.
Pas de texte explicatif avant ou après. Pas de blocs ``` autour.
Le fichier doit être directement enregistrable en .html et fonctionnel.

""";
    }

    // ═══ Result ═══

    public record AiResult(boolean success, String content, String message) {}
}
