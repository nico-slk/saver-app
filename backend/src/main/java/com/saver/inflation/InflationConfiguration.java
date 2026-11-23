package com.saver.inflation;

import com.saver.common.HttpJsonClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class InflationConfiguration {

    @Bean
    List<InflationProvider> inflationProviders(InflationProperties props, ObjectMapper json, Clock clock) {
        HttpJsonClient client = new HttpJsonClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build(),
                json);
        return List.of(
                new ArgentinaDatosInflationProvider(client, props.argentinaDatosUrl()),
                new BlsInflationProvider(client, props.blsUrl(), props.blsApiKey(), clock));
    }
}
