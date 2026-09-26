# Emissor Fiscal NFC-e — Empório Machado (GO)

API REST Spring Boot 3 (Java 17) para emissão de **NFC-e modelo 65** (layout 4.00) em
**homologação SEFAZ-GO**, separada do PDV Disk Cerveja. Usa a biblioteca
[`br.com.swconsultoria:java-nfe:4.1.3`](https://github.com/Samuel-Oliveira/Java_NFe).

> v1: apenas `AMBIENTE=homologacao`. Produção fica bloqueada de propósito.

## Pré-requisitos

- Java 17+
- Maven 3.9+
- Certificado A1 `.pfx` do CNPJ emitente
- CSC de homologação cadastrado no portal SEFAZ-GO
- Pasta `schemas/` (já incluída; origem: [schemas.zip Java_NFe](https://github.com/Samuel-Oliveira/Java_NFe/raw/master/schemas.zip))

## Configuração

```bash
cd emissor-fiscal
cp .env.example .env
# edite .env com senha do PFX, CSC real, IE e endereço
set -a && source .env && set +a
```

### Variáveis principais

| Variável | Descrição |
|----------|-----------|
| `CERT_PFX_PATH` | Caminho do `.pfx` (ex.: `~/Downloads/...pfx`) |
| `CERT_PFX_PASSWORD` | Senha do certificado |
| `AMBIENTE` | `homologacao` (obrigatório na v1) |
| `UF` | `GO` |
| `SERIE` / `NUMERO_INICIAL` | Série e próximo número da NFC-e |
| `NFCE_CSC_ID` / `NFCE_CSC` | Token CSC homolog (ID costuma ser `1`) |
| `EMITENTE_*` | CNPJ, razão, IE, CRT, endereço, município IBGE |
| `SCHEMAS_PATH` | Default `./schemas` |
| `SERVER_PORT` | Default `8090` |

### Emitente conhecido (placeholders)

- **CNPJ:** `33319836000102`
- **Razão:** ELCIANA DE PAULA MACHADO
- **Fantasia:** Empório Machado
- **UF:** GO
- **IE / endereço / CEP / código município:** preencha com os dados reais do cadastro SEFAZ

Não commite `.env`, `.pfx` nem senhas (já estão no `.gitignore`).

## Como rodar

```bash
mvn -q package -DskipTests
mvn spring-boot:run
# ou
java -jar target/emissor-fiscal-0.1.0-SNAPSHOT.jar
```

H2 file: `./data/emissor-fiscal`. Console: http://localhost:8090/h2-console  
(JDBC URL `jdbc:h2:file:./data/emissor-fiscal`, user `sa`, senha vazia).

### Docker (opcional)

```bash
docker build -t emissor-fiscal .
docker run --rm -p 8090:8090 \
  -e CERT_PFX_PATH=/certs/cert.pfx \
  -e CERT_PFX_PASSWORD=*** \
  -e NFCE_CSC=*** \
  -v "$HOME/Downloads:/certs:ro" \
  -v "$(pwd)/data:/app/data" \
  emissor-fiscal
```

## API

### `POST /api/notas`

```json
{
  "pedidoId": "venda-123",
  "destinatario": { "cpfCnpj": "00000000000", "nome": "Cliente" },
  "itens": [{
    "codigo": "CERV1",
    "descricao": "Cerveja 600ml",
    "ncm": "22030000",
    "cfop": "5102",
    "unidade": "UN",
    "quantidade": 2,
    "valorUnitario": 8.50,
    "cstOuCsosn": "102"
  }],
  "pagamentos": [{ "forma": "PIX", "valor": 17.00 }]
}
```

Em homologação o 1º item recebe automaticamente a descrição exigida pela SEFAZ
(`NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL`).

### `GET /api/notas/{id}`

Status: `ENVIANDO`, `AUTORIZADA`, `REJEITADA`, `CANCELADA`, `ERRO`, etc.

### `POST /api/notas/{id}/cancelar`

```json
{ "justificativa": "Cancelamento de teste homologacao" }
```

Prazo típico ~30 minutos após autorização (SEFAZ pode rejeitar fora do prazo).

## Integração futura com Disk Cerveja

Interface stub: `br.com.emporiomachado.emissorfiscal.client.DiskCervejaFiscalClient`.  
O PDV Angular/Spring pode chamar esta API via HTTP sem embutir lógica fiscal no monólito.

## Webservices SEFAZ-GO (homologação 4.00)

Em GO, NFC-e usa os **mesmos endpoints** da NF-e:

| Serviço | URL |
|---------|-----|
| Autorização | https://homolog.sefaz.go.gov.br/nfe/services/NFeAutorizacao4?wsdl |
| Retorno autorização | https://homolog.sefaz.go.gov.br/nfe/services/NFeRetAutorizacao4?wsdl |
| Eventos (cancelamento) | https://homolog.sefaz.go.gov.br/nfe/services/NFeRecepcaoEvento4?wsdl |
| Status | https://homolog.sefaz.go.gov.br/nfe/services/NFeStatusServico4?wsdl |
| Consulta protocolo | https://homolog.sefaz.go.gov.br/nfe/services/NFeConsultaProtocolo4?wsdl |
| Inutilização | https://homolog.sefaz.go.gov.br/nfe/services/NFeInutilizacao4?wsdl |

Fonte: [SEFAZ-GO — endereços 4.0 homologação](https://goias.gov.br/economia/enderecos-da-versao-4-0-em-homologacao/).  
A lib `java-nfe` resolve essas URLs internamente para `EstadosEnum.GO` + `AmbienteEnum.HOMOLOGACAO`.

Produção (referência, não usada na v1): `https://nfe.sefaz.go.gov.br/nfe/services/...`

## Impostos (v1)

Default: CRT=`1` (Simples Nacional) + CSOSN `102` + PIS/COFINS NT `49`.  
Ajuste `EMITENTE_CRT` / `cstOuCsosn` conforme o enquadramento real.

## Bloqueadores / cuidados

1. **Certificado A1** válido e senha corretos; cadeia trust SEFAZ (a lib traz cacerts).
2. **CSC** cadastrado em homologação no portal GO (mesmo com QR Code v3 online).
3. **IE e endereço** reais — placeholders geram rejeição SEFAZ.
4. **Numeração** não pode colidir com notas já autorizadas na série.
5. Empresa precisa estar **habilitada para NFC-e** em homologação na SEFAZ-GO.
6. Maven local: se `mvn` não existir, instale Maven 3.9+ ou use o wrapper quando disponível.
