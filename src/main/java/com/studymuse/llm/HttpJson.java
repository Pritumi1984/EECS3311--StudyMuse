package com.studymuse.llm;

import com.studymuse.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/** Small helper shared by the vendor adapters: POST a JSON body and return the parsed JSON reply. */
final class HttpJson {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();

    private HttpJson() {
    }

    static Map<String, Object> post(String url, Map<String, String> headers, Object body, String vendor) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.compact(body)));
        headers.forEach(b::header);
        HttpResponse<String> res;
        try {
            res = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new LLMException(vendor + " did not answer in time.", true, e);
        } catch (IOException e) {
            throw new LLMException("Could not reach " + vendor + ": " + e.getMessage(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException("Request to " + vendor + " was interrupted.", false, e);
        }
        int code = res.statusCode();
        if (code / 100 != 2) {
            String detail = res.body();
            try {
                Map<String, Object> err = Json.obj(Json.parseObject(res.body()).get("error"));
                if (err.get("message") != null) {
                    detail = String.valueOf(err.get("message"));
                }
            } catch (RuntimeException ignored) {
                // keep raw body
            }
            boolean retryable = code == 429 || code >= 500;
            String hint = (code == 401 || code == 403) ? " Check LLM_API_KEY in your .env file."
                    : code == 404 ? " Check LLM_MODEL in your .env file." : "";
            throw new LLMException(vendor + " error " + code + ": " + detail + hint, retryable);
        }
        try {
            return Json.parseObject(res.body());
        } catch (IllegalArgumentException e) {
            throw new LLMException(vendor + " returned an unreadable reply.", false, e);
        }
    }
}
