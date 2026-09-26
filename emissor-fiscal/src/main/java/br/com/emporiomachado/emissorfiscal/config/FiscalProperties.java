package br.com.emporiomachado.emissorfiscal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "emissor")
public record FiscalProperties(
        String certPfxPath,
        String certPfxPassword,
        @DefaultValue("homologacao") String ambiente,
        @DefaultValue("GO") String uf,
        @DefaultValue("1") int serie,
        @DefaultValue("1") long numeroInicial,
        @DefaultValue("1") String nfceCscId,
        String nfceCsc,
        @DefaultValue("./schemas") String schemasPath,
        @DefaultValue("true") boolean validarXml,
        Emitente emitente
) {
    public record Emitente(
            @DefaultValue("33319836000102") String cnpj,
            @DefaultValue("ELCIANA DE PAULA MACHADO") String razaoSocial,
            @DefaultValue("Empório Machado") String nomeFantasia,
            @DefaultValue("ISENTO") String ie,
            @DefaultValue("1") String crt,
            @DefaultValue("RUA EXEMPLO") String logradouro,
            @DefaultValue("S/N") String numero,
            String complemento,
            @DefaultValue("CENTRO") String bairro,
            @DefaultValue("5208707") String codigoMunicipio,
            @DefaultValue("GOIANIA") String municipio,
            @DefaultValue("74000000") String cep,
            @DefaultValue("6233333333") String fone
    ) {
        public String cnpjDigits() {
            return cnpj == null ? "" : cnpj.replaceAll("\\D", "");
        }

        public String cepDigits() {
            return cep == null ? "" : cep.replaceAll("\\D", "");
        }

        public String foneDigits() {
            return fone == null ? "" : fone.replaceAll("\\D", "");
        }
    }

    public boolean isHomologacao() {
        return ambiente == null || ambiente.equalsIgnoreCase("homologacao") || "2".equals(ambiente);
    }

    public String expandCertPath() {
        if (certPfxPath == null || certPfxPath.isBlank()) {
            return certPfxPath;
        }
        String path = certPfxPath.trim();
        // Remove aspas acidentais do .env (CERT_PFX_PATH="...")
        if ((path.startsWith("\"") && path.endsWith("\""))
                || (path.startsWith("'") && path.endsWith("'"))) {
            path = path.substring(1, path.length() - 1).trim();
        }
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
