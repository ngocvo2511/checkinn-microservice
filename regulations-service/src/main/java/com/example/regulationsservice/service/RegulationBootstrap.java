package com.example.regulationsservice.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Startup defaults retain the existing policy but now share the proxied source transaction. */
@Component
public class RegulationBootstrap implements ApplicationRunner {
    private final RegulationCacheProvider provider;
    public RegulationBootstrap(RegulationCacheProvider provider) { this.provider=provider; }
    @Override public void run(ApplicationArguments arguments) { provider.initializeCache(); }
}
