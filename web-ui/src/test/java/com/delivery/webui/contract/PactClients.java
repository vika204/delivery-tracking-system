package com.delivery.webui.contract;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** Builds a declarative HTTP client the same way web-ui does, but aimed at a Pact mock server. */
final class PactClients {

    private PactClients() {
    }

    static <T> T create(Class<T> type, String baseUrl) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(RestClient.builder().baseUrl(baseUrl).build()))
                .build()
                .createClient(type);
    }
}
