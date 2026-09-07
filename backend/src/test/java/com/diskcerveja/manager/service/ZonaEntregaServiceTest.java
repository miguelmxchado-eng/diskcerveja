package com.diskcerveja.manager.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZonaEntregaServiceTest {

    @Test
    void prefixoSimples() {
        assertTrue(ZonaEntregaService.cepCasaComZona("74810000", "74810"));
        assertTrue(ZonaEntregaService.cepCasaComZona("74810999", "74810"));
        assertFalse(ZonaEntregaService.cepCasaComZona("74811000", "74810"));
    }

    @Test
    void faixaDePrefixos() {
        assertTrue(ZonaEntregaService.cepCasaComZona("74000123", "74000-74099"));
        assertTrue(ZonaEntregaService.cepCasaComZona("74099999", "74000-74099"));
        assertFalse(ZonaEntregaService.cepCasaComZona("74100000", "74000-74099"));
    }

    @Test
    void faixaInvertidaAindaFunciona() {
        assertTrue(ZonaEntregaService.cepCasaComZona("74500000", "74999-74000"));
    }

    @Test
    void cepCompletoFormatado() {
        assertTrue(ZonaEntregaService.cepCasaComZona("74000000", "74000-000"));
        assertFalse(ZonaEntregaService.cepCasaComZona("74000001", "74000-000"));
    }

    @Test
    void listaComVariosTokens() {
        assertTrue(ZonaEntregaService.cepCasaComZona("74810111", "74000-74099, 74810"));
        assertTrue(ZonaEntregaService.cepCasaComZona("74050000", "74000-74099; 74810"));
    }

    @Test
    void cepInvalido() {
        assertFalse(ZonaEntregaService.cepCasaComZona("123", "74810"));
        assertFalse(ZonaEntregaService.cepCasaComZona("74810000", ""));
        assertFalse(ZonaEntregaService.cepCasaComZona(null, "74810"));
    }
}
