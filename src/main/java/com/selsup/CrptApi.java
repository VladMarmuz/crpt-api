package com.selsup;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final int requestLimit;
    private final TimeUnit timeUnit;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public CrptApi(int requestLimit, TimeUnit timeUnit) {
        this.timeUnit = Objects.requireNonNull(timeUnit);
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit);
        scheduler.scheduleAtFixedRate(this::releasePermits, 0, 1, timeUnit);
    }

    public void createDocument(Certificate certificate, String signature) throws InterruptedException, IOException {
        semaphore.acquire();
        try {
            sendRequest(certificate, signature);
        } finally {
            semaphore.acquire();
        }
    }

    private void sendRequest(Certificate certificate, String signature) throws IOException {
        String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";
        String json = mapper.writeValueAsString(certificate);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(1))
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(System.out::println)
                .join();
    }

    private void releasePermits() {
        semaphore.release(requestLimit - semaphore.availablePermits());
    }

    public static class Certificate {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }
}
