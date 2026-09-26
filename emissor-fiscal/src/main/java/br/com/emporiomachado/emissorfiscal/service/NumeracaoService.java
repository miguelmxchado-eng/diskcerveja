package br.com.emporiomachado.emissorfiscal.service;

import br.com.emporiomachado.emissorfiscal.config.FiscalProperties;
import br.com.emporiomachado.emissorfiscal.domain.NotaFiscalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NumeracaoService {

    private final NotaFiscalRepository repository;
    private final FiscalProperties properties;

    public NumeracaoService(NotaFiscalRepository repository, FiscalProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public synchronized long proximoNumero(int serie) {
        long max = repository.findMaxNumeroBySerie(serie);
        long inicial = Math.max(1, properties.numeroInicial());
        return Math.max(max + 1, inicial);
    }
}
