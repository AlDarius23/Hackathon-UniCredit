package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

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

public class BeMyHelpAI {

    private static final String API_KEY = System.getenv("Gemini api key");
    private static final String GEMINI_MODEL = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-2.5-flash");
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

    public static void main(String[] args) {
        String documentUniCredit;
        try {
            documentUniCredit = loadUniCreditContext();
            System.out.println("Documentul local UniCredit a fost incarcat: " + UNICREDIT_CONTEXT_FILE.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("Nu s-a putut incarca documentul local UniCredit: " + e.getMessage());
            return;
        }

        if (API_KEY == null || API_KEY.isBlank()) {
            System.out.println("Seteaza variabila de mediu 'Gemini api key' inainte sa rulezi aplicatia.");
            return;
        }

        int salariuLunarEuro = 1500;
        int economiiCurenteEuro = 2000;
        String mesajClient = "Vreau sa-mi iau o masina de 12.000 euro";
        String profilClientJson = """
                {
                  "status_profilare": "incomplet"
                }
                """;

        try {
            String prompt = buildSystemPrompt(mesajClient, profilClientJson, documentUniCredit);
            String jsonBody = buildGeminiRequestBody(prompt);

            System.out.println("Se analizeaza Life Radar-ul clientului cu documentul UniCredit local...");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Raspuns valid generat cu succes:\n");
                System.out.println(extractTextFromGeminiResponse(response.body()));
            } else {
                System.out.println("Eroare API: " + response.statusCode());
                System.out.println(response.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String loadUniCreditContext() throws IOException {
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

    private static String scrapeUniCreditContext(String curatedContext) {
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

    private static Set<String> extractUniCreditUrls(String text) {
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

    private static String extractRelevantUniCreditLines(String text) {
        StringBuilder relevant = new StringBuilder();
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
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

    private static String fallbackUniCreditContext() {
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

    private static String buildSystemPrompt(
            String mesajClient,
            String profilClientJson,
            String documentUniCredit
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
                3. Fii cald, valideaza emotiile sau temerile utilizatorului pentru a-i reduce anxietatea.
                4. Nu oferi sfaturi de investitii cu caracter legal si nu garanta aprobarea cererilor.
                5. Nu folosi expresii care creeaza obligatii pentru banca sau promisiuni ferme: "garantat", "sigur", "aprobat", "vei primi", "cea mai buna alegere", "trebuie sa iei".
                6. Evita in valorile JSON orice formulare care suna ca o indicatie ferma de cumparare. Foloseste formulari neutre precum "o optiune este", "poti analiza", "poate ajuta", "scenariu posibil".

                INAINTE DE PROFILARE:
                Inainte de orice profilare financiara, verifica Profilul Clientului Curent.
                Daca profilul lipseste, este incomplet sau nu contine raspunsuri clare la cele 4 intrebari de onboarding, nu face profilare si nu afisa produse.
                In acest caz, returneaza doar intrebarile de onboarding in formatul JSON pentru onboarding de mai jos.

                Intrebari obligatorii de onboarding:
                1. Spune-ne care este situatia ta in acest moment.
                   Optiuni:
                   - Invat (am o bursa sau ma sustin ai mei)
                   - Lucrez cu norma intreaga
                   - Am un job part-time sau proiecte ocazionale
                   - Sunt intr-o pauza sau imi caut drumul

                2. In ce interval te situezi cu banii intr-o luna obisnuita?
                   Optiuni:
                   - Pana in 2.000 Lei
                   - Intre 2.000 si 5.000 Lei
                   - Peste 5.000 Lei

                3. Alege lucrul pe care ai vrea sa-l rezolvi prima data legat de bani.
                   Optiuni:
                   - Banii se duc prea repede si nu stiu pe ce
                   - Vreau sa cumpar ceva mai scump si nu imi ajung banii
                   - Stau cu stresul ca poate aparea o cheltuiala neprevazuta

                4. Gandeste-te la o cheltuiala neprevazuta si urgenta de maine si spune-ne cum o rezolvi.
                   Optiuni:
                   - Ma descurc, am niste bani pusi deoparte pentru asa ceva
                   - E complicat, probabil ar trebui sa cer ajutor sau sa ma imprumut

                Format JSON pentru onboarding, folosit doar cand profilul este incomplet:
                {
                  "tip_raspuns": "onboarding_questions",
                  "validare_emotionala": "[Mesaj scurt si cald care reduce presiunea]",
                  "intrebari": [
                    {
                      "id": "situatie_curenta",
                      "intrebare": "Pune-ne care este situatia ta in acest moment.",
                      "optiuni": [
                        "Invat (am o bursa sau ma sustin ai mei)",
                        "Lucrez cu norma intreaga",
                        "Am un job part-time sau proiecte ocazionale",
                        "Sunt intr-o pauza sau imi caut drumul"
                      ]
                    },
                    {
                      "id": "interval_bani_lunar",
                      "intrebare": "In ce interval te situezi cu banii intr-o luna obisnuita.",
                      "optiuni": [
                        "Pana in 2.000 Lei",
                        "Intre 2.000 si 5.000 Lei",
                        "Peste 5.000 Lei"
                      ]
                    },
                    {
                      "id": "prima_problema_bani",
                      "intrebare": "Alege lucrul pe care ai vrea sa-l rezolvi prima data legat de bani.",
                      "optiuni": [
                        "Banii se duc prea repede si nu stiu pe ce",
                        "Vreau sa cumpar ceva mai scump si nu imi ajung banii",
                        "Stau cu stresul ca poate aparea o cheltuiala neprevazuta"
                      ]
                    },
                    {
                      "id": "cheltuiala_neprevazuta",
                      "intrebare": "Gandeste-te la o cheltuiala neprevazuta si urgenta de maine si spune-ne cum o rezolvi.",
                      "optiuni": [
                        "Ma descurc, am niste bani pusi deoparte pentru asa ceva",
                        "E complicat, probabil ar trebui sa cer ajutor sau sa ma imprumut"
                      ]
                    }
                  ],
                  "disclaimer": "BeMyHelp ofera scenarii si educatie financiara. Orice decizie finala iti apartine."
                }

                LOGICA DE ANALIZA A DATELOR, FOLOSITA DOAR DACA PROFILUL ESTE COMPLET:
                PASUL 1: Citeste si analizeaza INTEGRAL informatiile din [DATE_UNICREDIT].
                Trebuie sa ai in vedere TOATE ofertele posibile: pachete de cont, digitalizare, credite, investitii, asigurari si tot catalogul disponibil in [DATE_UNICREDIT] inainte de a formula solutiile.

                PASUL 2: Formuleaza solutiile pe baza [PROFIL_UTILIZATOR] si a nevoii curente.

                Daca analiza integrala a [DATE_UNICREDIT] a identificat oferte potrivite pentru nevoia utilizatorului:
                - "solutie_scurt_termen" este rezolvarea imediata, aleasa ca optiune potrivita din intregul portofoliu scanat.
                - "solutie_lung_termen" este planul educational si de siguranta pentru viitor, selectat din restul ecosistemului de oferte.

                Daca analiza integrala a [DATE_UNICREDIT] nu a identificat oferte potrivite:
                - "solutie_scurt_termen" ofera un sfat general de optimizare a bugetului adaptat la profil.
                - "solutie_lung_termen" propune construirea unui fond de urgenta.

                Regula speciala pentru masina:
                Pentru masina, autoturism sau achizitie personala, produsul afisat trebuie sa fie exclusiv "Credit de Realizari Personale".
                Nu afisa credit ipotecar pentru masina.

                DATE DINAMICE DE REFERINTA:
                [DATE_UNICREDIT]:
                %s

                [PROFIL_UTILIZATOR]:
                %s

                [NEVOIE_CURENTA]:
                "%s"

                FORMATUL STRICT DE RASPUNS PENTRU PROFIL COMPLET:
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
                - Respecta exact una dintre cele doua structuri JSON de mai sus: onboarding daca profilul este incomplet, formatul strict cu 5 chei daca profilul este complet.
                - Nu adauga chei noi.
                - Nu folosi in valorile JSON formulari care suna ca o indicatie ferma de cumparare.
                - Foloseste cheia de produs doar ca nume tehnic de camp, deoarece face parte din schema ceruta.
                - Pentru campul de produs scrie doar numele produselor gasite in document sau "Nu am detalii in document".
                - Nu inventa dobanzi, conditii, beneficii sau pasi.
                - Cand folosesti dobanda, limita, perioada sau nume de produs, acestea trebuie sa existe in [DATE_UNICREDIT].
                """, documentUniCredit, profilClientJson, mesajClient);
    }

    private static String buildGeminiRequestBody(String prompt) {
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

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String extractTextFromGeminiResponse(String rawResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(rawResponse);
            JsonNode candidatesNode = rootNode.path("candidates");

            if (candidatesNode.isArray() && candidatesNode.size() > 0) {
                JsonNode partsNode = candidatesNode.get(0).path("content").path("parts");

                if (partsNode.isArray() && partsNode.size() > 0) {
                    return partsNode.get(0).path("text").asText();
                } else {
                    return "Eroare: Structura 'parts' lipseste din raspunsul generat.";
                }
            } else {
                return "Eroare: Structura 'candidates' lipseste din raspunsul generat.";
            }
        } catch (Exception e) {
            return "Eroare interna la procesarea raspunsului brut: " + e.getMessage();
        }
    }
}
