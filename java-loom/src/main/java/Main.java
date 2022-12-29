import jdk.incubator.concurrent.StructuredTaskScope;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.IntStream;

// Note: Intentionally, code is not shared across scenarios
public class Main {

    static class Scenarios {
        private final URI url;
        private final HttpClient client;

        Scenarios(URI url) {
            this.url = url;
            this.client = HttpClient.newHttpClient();
        }

        public String scenario1() throws ExecutionException, InterruptedException {
            var req = HttpRequest.newBuilder(url.resolve("/1")).build();
            try (var scope = new StructuredTaskScope.ShutdownOnSuccess<HttpResponse<String>>()) {
                scope.fork(() -> client.send(req, HttpResponse.BodyHandlers.ofString()));
                scope.fork(() -> client.send(req, HttpResponse.BodyHandlers.ofString()));
                scope.join();
                return scope.result().body();
            }
        }


        public String scenario2() throws ExecutionException, InterruptedException {
            var req = HttpRequest.newBuilder(url.resolve("/2")).build();
            try (var scope = new StructuredTaskScope.ShutdownOnSuccess<HttpResponse<String>>()) {
                scope.fork(() -> client.send(req, HttpResponse.BodyHandlers.ofString()));
                scope.fork(() -> client.send(req, HttpResponse.BodyHandlers.ofString()));
                scope.join();
                return scope.result().body();
            }
        }


        public String scenario3() throws ExecutionException, InterruptedException {
            var req = HttpRequest.newBuilder(url.resolve("/3")).build();
            try (var scope = new StructuredTaskScope.ShutdownOnSuccess<HttpResponse<String>>()) {
                IntStream.rangeClosed(1, 10_000)
                        .forEach(i ->
                                scope.fork(() ->
                                        client.send(req, HttpResponse.BodyHandlers.ofString())
                                )
                        );

                scope.join();
                return scope.result().body();
            }
        }


        public String scenario4() throws ExecutionException, InterruptedException {
            var req = HttpRequest.newBuilder(url.resolve("/4")).build();
            try (var outer = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
                outer.fork(() -> {
                    try (var inner = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
                        inner.fork(() -> client.send(req, HttpResponse.BodyHandlers.ofString()).body());
                        inner.joinUntil(Instant.now().plusSeconds(1));
                        return inner.result();
                    }
                });

                outer.fork(() -> client.send(req, HttpResponse.BodyHandlers.ofString()).body());

                outer.join();

                return outer.result();
            }
        }


        public String scenario5() throws ExecutionException, InterruptedException {
            class Req {
                final HttpRequest req = HttpRequest.newBuilder(url.resolve("/5")).build();

                HttpResponse<String> make() throws Exception {
                    var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        return resp;
                    } else {
                        throw new Exception("invalid response");
                    }
                }
            }

            try (var scope = new StructuredTaskScope.ShutdownOnSuccess<HttpResponse<String>>()) {
                scope.fork(() -> new Req().make());
                scope.fork(() -> new Req().make());
                scope.join();
                return scope.result().body();
            }
        }


        public String scenario6() throws ExecutionException, InterruptedException {
            class Req {
                final HttpRequest req = HttpRequest.newBuilder(url.resolve("/6")).build();

                HttpResponse<String> make() throws Exception {
                    var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        return resp;
                    } else {
                        throw new Exception("invalid response");
                    }
                }
            }

            try (var scope = new StructuredTaskScope.ShutdownOnSuccess<HttpResponse<String>>()) {
                scope.fork(() -> new Req().make());
                scope.fork(() -> new Req().make());
                scope.fork(() -> new Req().make());
                scope.join();
                return scope.result().body();
            }
        }


        public String scenario7() throws ExecutionException, InterruptedException {
            var req = HttpRequest.newBuilder(url.resolve("/7")).build();
            try (var scope = new StructuredTaskScope.ShutdownOnSuccess<HttpResponse<String>>()) {
                scope.fork(() -> client.send(req, HttpResponse.BodyHandlers.ofString()));
                scope.fork(() -> {
                    Thread.sleep(3000);
                    return client.send(req, HttpResponse.BodyHandlers.ofString());
                });
                scope.join();
                return scope.result().body();
            }
        }


        public String scenario8() throws InterruptedException, ExecutionException {
            class Req implements AutoCloseable {
                final HttpRequest openReq =
                        HttpRequest.newBuilder(url.resolve("/8?open")).build();
                final Function<String, HttpRequest> useReq = (id) ->
                        HttpRequest.newBuilder(url.resolve("/8?use=" + id)).build();
                final Function<String, HttpRequest> closeReq = (id) ->
                        HttpRequest.newBuilder(url.resolve("/8?close=" + id)).build();

                final String id;

                public Req() throws IOException, InterruptedException {
                    id = client.send(openReq, HttpResponse.BodyHandlers.ofString()).body();
                }

                HttpResponse<String> make() throws Exception {
                    var resp = client.send(useReq.apply(id), HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        return resp;
                    } else {
                        throw new Exception("invalid response");
                    }
                }

                @Override
                public void close() throws IOException, InterruptedException {
                    client.send(closeReq.apply(id), HttpResponse.BodyHandlers.ofString()).body();
                }
            }

            try (var scope = new StructuredTaskScope.ShutdownOnSuccess<HttpResponse<String>>()) {
                scope.fork(() -> {
                    try (var req = new Req()) {
                        return req.make();
                    }
                });
                scope.fork(() -> {
                    try (var req = new Req()) {
                        return req.make();
                    }
                });

                scope.join();

                return scope.result().body();
            }
        }

        List<String> results() throws ExecutionException, InterruptedException {
            return List.of(scenario1(), scenario2(), scenario3(), scenario4(), scenario5(), scenario6(), scenario7(), scenario8());
            //return List.of(scenario4());
        }
    }


    public static void main(String[] args) throws URISyntaxException, ExecutionException, InterruptedException {
        var scenarios = new Scenarios(new URI("http://localhost:8080"));
        scenarios.results().forEach(System.out::println);
    }

}