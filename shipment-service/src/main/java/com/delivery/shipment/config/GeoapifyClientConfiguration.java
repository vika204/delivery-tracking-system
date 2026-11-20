package com.delivery.shipment.config;

import com.delivery.shipment.geo.GeoapifyApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(group = "geoapify", types = GeoapifyApi.class)
public class GeoapifyClientConfiguration {
}
