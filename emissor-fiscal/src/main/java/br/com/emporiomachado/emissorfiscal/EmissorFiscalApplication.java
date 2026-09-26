package br.com.emporiomachado.emissorfiscal;

import br.com.emporiomachado.emissorfiscal.config.FiscalProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FiscalProperties.class)
public class EmissorFiscalApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmissorFiscalApplication.class, args);
    }
}
