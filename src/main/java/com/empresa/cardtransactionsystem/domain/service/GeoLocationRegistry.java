package com.empresa.cardtransactionsystem.domain.service;

import com.empresa.cardtransactionsystem.domain.model.GeoLocation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class GeoLocationRegistry {

    private static final Logger log = LoggerFactory.getLogger(GeoLocationRegistry.class);
    private static final String RESOURCE_PATH = "geo/test-locations.json";

    private final ObjectMapper objectMapper;
    private Map<String, GeoLocation> byCode;
    private List<GeoLocation> all;

    public GeoLocationRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        List<GeoLocation> entries = objectMapper.readValue(
                resource.getInputStream(),
                new TypeReference<>() {});
        all = List.copyOf(entries);
        byCode = all.stream().collect(Collectors.toMap(GeoLocation::code, Function.identity()));
        log.info("GeoLocationRegistry loaded {} locations", all.size());
    }

    public Optional<GeoLocation> findByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(byCode.get(code.toUpperCase()));
    }

    public GeoLocation random() {
        return all.get(ThreadLocalRandom.current().nextInt(all.size()));
    }
}
