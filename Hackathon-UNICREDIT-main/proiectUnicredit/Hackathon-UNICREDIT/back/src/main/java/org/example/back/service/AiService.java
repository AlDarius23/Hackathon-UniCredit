package org.example.back.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;

@Service
public class AiService {

    private static final String API_KEY = System.getenv("Gemini api key");
    private static final String GEMINI_MODEL = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.1-flash-lite");
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
            + GEMINI_MODEL + ":generateContent?key=" + API_KEY;
    private static final Path UNICREDIT_CONTEXT_FILE = Path.of("unicredit-info.txt");
    private static final Path UNICREDIT_JSOUP_CACHE_FILE = Path.of("unicredit-jsoup-cache.txt");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)]+");
    private static final int MAX_JSOUP_CONTEXT_CHARS = 30_000;
    private static final List<String> UNICREDIT_URLS = List.of(
            "https://www.unicredit.ro/ro/persoane-fizice/Credite/credite-realizari-personale.html",
            "https://www.unicredit.ro/ro/persoane-fizice/Credite/credite-realizari-personale/credite-realizari-personale-MB.html",
            "https://www.unicredit.ro/ro/persoane-fizice/asistenta/credite-de-nevoi-personale.html",
            "https://www.unicredit.ro/ro/persoane-fizice/Credite/credite-ipoteca.html"
    );

    private String documentUniCredit = "";

    @PostConstruct
    public void init() {
        try {
            this.documentUniCredit = loadUniCreditContext();
            System.out.println("Documentul local UniCredit a fost incarcat: " + UNICREDIT_CONTEXT_FILE.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("Nu s-a putut incarca documentul local UniCredit: " + e.getMessage());
        }
    }

    public String getChatResponse(String mesajClient, String profilClientJson, String istoricConversatie) {
        if (API_KEY == null || API_KEY.isBlank()) {
            return "{\"validare_emotionala\": \"Seteaza variabila de mediu 'Gemini api key' inainte sa rulezi aplicatia.\", \"solutie_scurt_termen\":\"\", \"solutie_lung_termen\":\"\", \"produs_unicredit_recomandat\":\"\", \"disclaimer\":\"\"}";
        }

        if (profilClientJson == null || profilClientJson.isBlank() || profilClientJson.equals("{}")) {
            profilClientJson = """
                    {
                      "status_profilare": "complet"
                    }
                    """;
        }

        try {
            String prompt = buildSystemPrompt(mesajClient, profilClientJson, this.documentUniCredit, istoricConversatie);
            String jsonBody = buildGeminiRequestBody(prompt);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractTextFromGeminiResponse(response.body());
            } else if (response.statusCode() == 429) {
                return "{\"validare_emotionala\": \"Sistemul a atins limita de mesaje pe minut (Eroare 429 Too Many Requests). Te rog așteaptă aproximativ un minut și încearcă din nou.\", \"solutie_scurt_termen\":\"\", \"solutie_lung_termen\":\"\", \"produs_unicredit_recomandat\":\"\", \"disclaimer\":\"\"}";
            } else {
                return "{\"validare_emotionala\": \"Eroare API: " + response.statusCode() + "\", \"solutie_scurt_termen\":\"\", \"solutie_lung_termen\":\"\", \"produs_unicredit_recomandat\":\"\", \"disclaimer\":\"\"}";
            }
        } catch (Exception e) {
            return "{\"validare_emotionala\": \"Eroare: " + e.getMessage() + "\", \"solutie_scurt_termen\":\"\", \"solutie_lung_termen\":\"\", \"produs_unicredit_recomandat\":\"\", \"disclaimer\":\"\"}";
        }
    }

    private String loadUniCreditContext() throws IOException {
        if (!Files.exists(UNICREDIT_CONTEXT_FILE)) {
            String fallbackContext = fallbackUniCreditContext();
            Files.writeString(UNICREDIT_CONTEXT_FILE, fallbackContext, StandardCharsets.UTF_8);
        }

        String curatedContext = Files.readString(UNICREDIT_CONTEXT_FILE, StandardCharsets.UTF_8);
        String scrapedContext = scrapeUniCreditContext(curatedContext);
        Files.writeString(UNICREDIT_JSOUP_CACHE_FILE, scrapedContext, StandardCharsets.UTF_8);

        return """
                [DOCUMENT_UNICREDIT_LOCAL]
                %s

                [COMPLETARI_JSOUP_DIN_LINKURILE_DOCUMENTULUI]
                %s
                """.formatted(curatedContext, scrapedContext);
    }

    private String scrapeUniCreditContext(String curatedContext) {
        StringBuilder context = new StringBuilder();
        context.append("Cache Jsoup generat la ")
                .append(LocalDateTime.now())
                .append("\n\n");

        Set<String> urls = extractUniCreditUrls(curatedContext);
        urls.addAll(UNICREDIT_URLS);

        for (String url : urls) {
            if (context.length() >= MAX_JSOUP_CONTEXT_CHARS) {
                context.append("JSOUP_LIMIT: Cache-ul a fost limitat pentru a pastra promptul utilizabil.\n");
                break;
            }

            if (url.toLowerCase().contains(".pdf")) {
                context.append("SURSA: ").append(url).append("\n");
                context.append("JSOUP_STATUS: SKIPPED_PDF - link pastrat in documentul local, dar continutul PDF nu este parsabil corect cu Jsoup HTML.\n\n");
                continue;
            }

            try {
                Document page = Jsoup.connect(url)
                        .userAgent("bemyhelpai/1.0")
                        .timeout(7_000)
                        .get();

                String pageText = page.select("main, body").text()
                        .replaceAll("\\s+", " ")
                        .trim();

                context.append("SURSA: ").append(url).append("\n");
                context.append("JSOUP_STATUS: OK\n");
                context.append(extractRelevantUniCreditLines(pageText)).append("\n\n");
            } catch (IOException e) {
                context.append("SURSA: ").append(url).append("\n");
                context.append("Nu s-au putut extrage date cu Jsoup: ")
                        .append(e.getMessage())
                        .append("\n\n");
            }
        }

        context.append("""
                REGULI INTERNE BEMYHELP:
                - Pentru intentii despre masina, autoturism sau achizitie personala se afiseaza exclusiv Credit de Realizari Personale ca optiune de produs.
                - Pentru intrebari simple de suport sau forum se afiseaza solutia gasita in documentul local.
                - Daca o informatie nu apare in acest document local, raspunsul trebuie sa spuna explicit ca nu are detalii.
                """);
        return context.toString();
    }

    private Set<String> extractUniCreditUrls(String text) {
        Set<String> urls = new LinkedHashSet<>();
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String url = matcher.group().replaceAll("[,.;]+$", "");
            if (url.contains("unicredit.ro")) {
                urls.add(url);
            }
        }
        return urls;
    }

    private String extractRelevantUniCreditLines(String text) {
        StringBuilder relevant = new StringBuilder();
        for (String sentence : text.split("(?<=[.!?])\\\\s+")) {
            String normalized = sentence.toLowerCase();
            if (normalized.contains("creditul de realizari personale")
                    || normalized.contains("credit de realizari personale")
                    || normalized.contains("dobanda")
                    || normalized.contains("dae")
                    || normalized.contains("pana la")
                    || normalized.contains("ramburs")
                    || normalized.contains("eligibil")
                    || normalized.contains("mobile banking")
                    || normalized.contains("credit ipotecar")
                    || normalized.contains("parola")
                    || normalized.contains("suport")) {
                relevant.append("- ").append(sentence.trim()).append("\n");
            }
        }

        if (relevant.isEmpty()) {
            return "Nu au fost identificate linii relevante pentru produse sau suport.";
        }

        return relevant.length() > 4_000 ? relevant.substring(0, 4_000) : relevant.toString();
    }

    private String fallbackUniCreditContext() {
        return """
                Document local UniCredit generat ca fallback cand Jsoup nu poate accesa site-ul.

                SURSA: https://www.unicredit.ro/ro/persoane-fizice/Credite/credite-realizari-personale.html
                - Creditul de Realizari Personale este oferit de UniCredit Consumer Financing IFN S.A. prin UniCredit Bank S.A.
                - Produsul mentioneaza o dobanda anuala fixa de la 6,49% pana la 20,99%.
                - Produsul mentioneaza finantare pana la 250.000 lei.
                - Produsul mentioneaza rambursare intre 12 si 60 de luni.
                - Conditiile de eligibilitate mentioneaza varsta minima 22 ani si varsta maxima 70 ani, cu exceptii pentru navigatori.

                SURSA: https://www.unicredit.ro/ro/persoane-fizice/Credite/credite-realizari-personale/credite-realizari-personale-MB.html
                - Creditul de Realizari Personale 100% Mobile poate fi accesat din Mobile Banking.
                - Produsul 100% Mobile mentioneaza imprumut pana la 200.000 lei.
                - Produsul 100% Mobile mentioneaza perioada flexibila de rambursare intre 12 si 60 de luni.

                REGULI INTERNE BEMYHELP:
                - Pentru intentii despre masina, autoturism sau achizitie personala se afiseaza exclusiv Credit de Realizari Personale ca optiune de produs.
                - Pentru intrebari simple de suport sau forum se afiseaza solutia gasita in documentul local.
                - Daca o informatie nu apare in acest document local, raspunsul trebuie sa spuna explicit ca nu are detalii.
                """;
    }

    private String buildSystemPrompt(
            String mesajClient,
            String profilClientJson,
            String documentUniCredit,
            String istoricConversatie
    ) {
        return String.format("""
                Esti BeMyHelp, asistentul si educatorul financiar al UniCredit Bank Romania.
                Audienta ta are cunostinte financiare zero si sufera adesea de anxietate sociala cand vine vorba de bani.

                Foloseste STRICT datele din [DATE_UNICREDIT] si [PROFIL_UTILIZATOR].
                Nu folosi surse externe, cunostinte generale, internet, presupuneri sau informatii care nu apar in [DATE_UNICREDIT].
                Daca informatia nu este in [DATE_UNICREDIT], spune simplu ca nu ai detalii.

                REGULI STRICTE DE COMPORTAMENT:
                1. Explica direct, la obiect si extrem de simplu. Nu folosi analogii.
                2. Nu folosi absolut niciun termen bancar fara sa il traduci pe intelesul unui om simplu.
                3. Tonul tău trebuie să fie cald, prietenos și extrem de clar. Doar dacă utilizatorul exprimă direct stres, frustrare sau o problemă urgentă, validează-i scurt emoțiile. Pentru întrebări simple sau factuale, răspunde direct și la obiect, fără să aduci vorba despre anxietate sau temeri. Evită clișeele. Nu începe propozițiile mereu cu expresii precum 'Înțeleg perfect că...' sau 'E normal să...'. Variază modul în care oferi suport.
                4. Nu oferi sfaturi de investitii cu caracter legal si nu garanta aprobarea cererilor.
                5. Nu folosi expresii care creeaza obligatii pentru banca sau promisiuni ferme: "garantat", "sigur", "aprobat", "vei primi", "cea mai buna alegere", "trebuie sa iei".
                6. Evita in valorile JSON orice formulare care suna ca o indicatie ferma de cumparare. Foloseste formulari neutre precum "o optiune este", "poti analiza", "poate ajuta", "scenariu posibil".

                LOGICA DE ANALIZA A DATELOR:
                PASUL 1: Citeste si analizeaza INTEGRAL informatiile din [DATE_UNICREDIT].
                Trebuie sa ai in vedere TOATE ofertele posibile: pachete de cont, digitalizare, credite, investitii, asigurari si tot catalogul disponibil in [DATE_UNICREDIT] inainte de a formula solutiile.

                PASUL 2: Formuleaza solutiile pe baza [PROFIL_UTILIZATOR] si a nevoii curente.

                Daca nevoia curenta este doar un salut sau o cerere generala de ajutor (fara o problema financiara specifica):
                - Completeaza doar raspunsul tau conversational in campul "validare_emotionala".
                - Lasa campurile "solutie_scurt_termen", "solutie_lung_termen", "produs_unicredit_recomandat" si "disclaimer" goale (sir de caractere gol "").

                Daca nevoia curenta exprima clar o intentie sau o problema financiara:
                - Daca analiza integrala a [DATE_UNICREDIT] a identificat oferte potrivite:
                  - "solutie_scurt_termen" este rezolvarea imediata, aleasa ca optiune potrivita din intregul portofoliu scanat.
                  - "solutie_lung_termen" este planul educational si de siguranta pentru viitor, selectat din restul ecosistemului de oferte.
                - Daca analiza integrala a [DATE_UNICREDIT] nu a identificat oferte potrivite:
                  - Dacă utilizatorul cere doar detalii despre un produs/serviciu care nu există în [DATE_UNICREDIT] (ex. crypto, anumite carduri), completează doar validare_emotionala spunând direct că nu ai informația. Lasă câmpurile de soluții și recomandări goale (""). NU oferi sfaturi nesolicitate despre bugete sau fonduri de urgență dacă utilizatorul nu a cerut ajutor explicit în acest sens.
                  - Doar dacă utilizatorul cere explicit ajutor cu gestionarea banilor și nu ai un produs relevant, folosește "solutie_scurt_termen" pentru un sfat general de bugetare.

                Regula speciala pentru masina:
                Pentru masina, autoturism sau achizitie personala, produsul afisat trebuie sa fie exclusiv "Credit de Realizari Personale".
                Nu afisa credit ipotecar pentru masina.

                DATE DINAMICE DE REFERINTA:
                [DATE_UNICREDIT]:
                %s

                [PROFIL_UTILIZATOR]:
                %s

                [ISTORIC_CONVERSATIE]:
                %s

                [NEVOIE_CURENTA]:
                "%s"

                FORMATUL STRICT DE RASPUNS:
                Esti obligat sa returnezi raspunsul tau EXCLUSIV ca un obiect JSON valid, fara niciun alt text in afara acoladelor.
                Variabilele trebuie sa ramana exact acestea:

                {
                  "validare_emotionala": "Mesaj cald, empatic, care ii da dreptate si il linisteste.",
                  "solutie_scurt_termen": "Rezolvarea imediata aleasa dupa scanarea completa a tuturor ofertelor.",
                  "solutie_lung_termen": "Planul educational si de siguranta pentru viitor.",
                  "produs_unicredit_recomandat": "Numele exact al produsului oficial din [DATE_UNICREDIT], daca se aplica.",
                  "disclaimer": "BeMyHelp ofera scenarii si educatie financiara. Orice decizie finala iti apartine."
                }

                Reguli pentru valorile JSON:
                - Respecta exact structura JSON de mai sus. Toate cheile trebuie sa existe.
                - Când redactezi valorile pentru JSON, integrează soluțiile sub formă de text natural, cursiv. ESTE STRICT INTERZIS să folosești etichete explicite de tipul 'Pe termen scurt:' sau 'Pe termen lung:' în corpul mesajelor tale.
                - Nu adauga chei noi.
                - Daca nu ai identificat o problema clara, lasa goale ("") cheile de solutii, produs si disclaimer.
                - Nu folosi in valorile JSON formulari care suna ca o indicatie ferma de cumparare.
                - Foloseste cheia de produs doar ca nume tehnic de camp, deoarece face parte din schema ceruta.
                - Pentru campul de produs scrie doar numele produselor gasite in document sau "".
                - Nu inventa dobanzi, conditii, beneficii sau pasi.
                - Cand folosesti dobanda, limita, perioada sau nume de produs, acestea trebuie sa existe in [DATE_UNICREDIT].
                """, documentUniCredit, profilClientJson, istoricConversatie, mesajClient);
    }

    private String buildGeminiRequestBody(String prompt) {
        return String.format("""
                {
                  "contents": [{
                    "parts": [{"text": "%s"}]
                  }],
                  "generationConfig": {
                    "responseMimeType": "application/json"
                  }
                }
                """, escapeJson(prompt));
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String extractTextFromGeminiResponse(String rawResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(rawResponse);
            JsonNode candidatesNode = rootNode.path("candidates");

            if (candidatesNode.isArray() && candidatesNode.size() > 0) {
                JsonNode partsNode = candidatesNode.get(0).path("content").path("parts");

                if (partsNode.isArray() && partsNode.size() > 0) {
                    return partsNode.get(0).path("text").asText();
                } else {
                    return "{\"validare_emotionala\":\"Eroare: Structura 'parts' lipseste din raspunsul generat.\"}";
                }
            } else {
                return "{\"validare_emotionala\":\"Eroare: Structura 'candidates' lipseste din raspunsul generat.\"}";
            }
        } catch (Exception e) {
            return "{\"validare_emotionala\":\"Eroare interna la procesarea raspunsului brut: " + e.getMessage() + "\"}";
        }
    }
}
