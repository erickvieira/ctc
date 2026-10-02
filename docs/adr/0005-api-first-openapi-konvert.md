# 0005. API-first com openapi-generator (kotlin-spring) + mapeamento Konvert

- Status: Aceito
- Data: 2026-10-02

## Contexto

O contrato HTTP é um artefato do desafio e precisa ser lido por humanos. Gerar código a partir do contrato
elimina a divergência entre documentação e implementação e reduz boilerplate de controller/DTO.

## Decisão

**Contrato como fonte de verdade**, modular para leitura: `openapi/api.yaml` (raiz) + `openapi/endpoints/<path>.yaml`
+ `openapi/schemas/<Schema>.yaml`, referenciados por `$ref` relativos.

Geração com **openapi-generator CLI 7.25.0**, generator `kotlin-spring` com `interfaceOnly=true`,
`useSpringBoot4/useJackson3=true` e `requestMappingMode=api_interface`. Os controllers **implementam** as
interfaces geradas; os modelos gerados ficam restritos ao adaptador web.

O mapeamento domínio↔DTO usa **Konvert 4.5.1** (`@Konverter`), que gera `object <Name>Impl` (sem bean/DI,
chamado direto).

Ranges de `quantity`/`capacity` **não** viram `minimum`/`maximum` no schema: o limite é configurável em
runtime, então a regra vive no value object e o contrato a documenta via `description`/`example`.

## Consequências

- Contrato único e executável; `springdoc` serve a UI a partir dele.
- Código gerado é isolado no adaptador (`adapter.input.web.api`/`.model`) e excluído de JaCoCo/PIT.
- Toolchain: o plugin Gradle do openapi-generator parou na 7.14 (sem `useSpringBoot4`/`useJackson3`), então a
  geração roda pelo **CLI via `JavaExec`** (`openapiGenerate`).

## Alternativas rejeitadas

- **Code-first (springdoc a partir dos controllers)**: o contrato deixaria de ser a fonte de verdade.
- **Mappers manuais**: mais código e mais chance de divergência campo a campo.
- **Ranges estáticos no schema**: gerariam Bean Validation divergente do limite configurável.
