# Pacote de reprodução do TCC

Este diretório permite refazer, em qualquer máquina, as medições do artigo *Análise de manutenibilidade em arquiteturas de software: um estudo comparativo entre monólito tradicional e monólito modular no ecossistema Java* e conferir que os números são os mesmos.

O script mede as duas versões com os mesmos instrumentos:

| Versão | Origem |
|---|---|
| Baseline (monólito tradicional) | commit `acd6c2b`, branch `main` |
| Modular (monólito modular) | checkout atual (tag `tcc-final`, branch `refatoracao-modular`) |

## O que é reproduzido

| Arquivo | Conteúdo | No artigo |
|---|---|---|
| `resultados/RESUMO.md` | tabelas-resumo de tudo abaixo | — |
| `resultados/<versão>/testes.csv` | testes por classe e total | Seções 5.2 e 5.8 |
| `resultados/<versão>/encapsulamento.*` | indicadores de encapsulamento (`EncapsulationReport`) | Tabela 2 |
| `resultados/<versão>/martin-metrics.*` | métricas de pacote de Martin (`MartinMetricsReport`) | Tabela 3 |
| `ambiente.txt` | SO, JDK, Gradle e dependências da última execução | Quadro 2 |

O SonarQube (Tabelas 5 e 6) fica de fora, porque precisa de um servidor e de um token. Os cenários de mudança (Tabela 7) estão nas branches `cenario-c1-main`, `cenario-c1-modular`, `cenario-c2-main` e `cenario-c2-modular`.

## Pré-requisitos

- JDK 21
- Docker em execução (os testes de integração com MySQL e PostgreSQL usam Testcontainers)
- Python 3 e Git

## Como reproduzir

```bash
git checkout tcc-final
./reproducao/reproduzir.sh
git status --porcelain reproducao/resultados   # vazio = resultados idênticos aos do artigo
```

A execução leva poucos minutos. O workflow `.github/workflows/reproducao.yml` faz o mesmo no GitHub Actions (Ubuntu, JDK 21) e falha se algum resultado mudar.

## Observação sobre a baseline

A baseline declara o Spring Modulith 1.2.0, feito para o Spring Boot 3.3. Para que as duas versões usem a mesma linha compatível com o Spring Boot 4.1, o script troca essa versão por 2.1.1 no worktree temporário da baseline. Nada é commitado na `main`, e o código da aplicação não é alterado.
