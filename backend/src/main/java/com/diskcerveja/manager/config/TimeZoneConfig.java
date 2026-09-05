package com.diskcerveja.manager.config;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeZoneConfig {

    public static final String ZONE = "America/Sao_Paulo";

    @PostConstruct
    void applyDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }
}
