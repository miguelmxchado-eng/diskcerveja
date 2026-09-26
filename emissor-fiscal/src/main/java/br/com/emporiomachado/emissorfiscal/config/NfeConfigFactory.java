package br.com.emporiomachado.emissorfiscal.config;

import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class NfeConfigFactory {

    private final FiscalProperties properties;

    public NfeConfigFactory(FiscalProperties properties) {
        this.properties = properties;
    }

    public ConfiguracoesNfe criar() throws Exception {
        String certPath = properties.expandCertPath();
        if (certPath == null || certPath.isBlank()) {
            throw new IllegalStateException("CERT_PFX_PATH não configurado");
        }
        if (properties.certPfxPassword() == null || properties.certPfxPassword().isBlank()) {
            throw new IllegalStateException("CERT_PFX_PASSWORD não configurado");
        }
        Path schemas = Path.of(properties.schemasPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(schemas)) {
            throw new IllegalStateException("Pasta de schemas não encontrada: " + schemas
                    + " (baixe schemas.zip do Java_NFe e extraia em ./schemas)");
        }

        Certificado certificado = CertificadoService.certificadoPfx(certPath, properties.certPfxPassword());
        EstadosEnum estado = EstadosEnum.valueOf(properties.uf().trim().toUpperCase());
        AmbienteEnum ambiente = properties.isHomologacao() ? AmbienteEnum.HOMOLOGACAO : AmbienteEnum.PRODUCAO;

        ConfiguracoesNfe config = ConfiguracoesNfe.criarConfiguracoes(
                estado,
                ambiente,
                certificado,
                schemas.toString()
        );
        config.setTimeout(40_000);
        return config;
    }
}
