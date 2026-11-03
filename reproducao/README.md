# Pacote de reprodução do TCC

Este diretório permite refazer, em qualquer máquina, as medições do artigo *Análise de manutenibilidade em arquiteturas de software: um estudo comparativo entre monólito tradicional e monólito modular no ecossistema Java* e conferir que os números são os mesmos.

O script mede todas as versões usadas no estudo com os mesmos instrumentos:

| Versão | Commit | Branch |
|---|---|---|
| Baseline (monólito tradicional) | `acd6c2b` | `main` |
| Modular (monólito modular) | checkout atual | `refatoracao-modular`, tag `tcc-final` |
| Modular sem a visita inicial automática (controle) | `a9481c3` | `modular-sem-visita-inicial` |
| Base modular dos cenários | `49d9597` | — |
| Cenário C1, baseline / modular | `d1b9611` / `63771a5` | `cenario-c1-main` / `cenario-c1-modular` |
| Cenário C2, baseline / modular | `34c36ba` / `86eace8` | `cenario-c2-main` / `cenario-c2-modular` |
| Critérios de aceitação do C1, baseline / modular | `eecef40` / `f679f73` | um commit acima do cenário, nas mesmas branches |
| Critérios de aceitação do C2, baseline / modular | `953d789` / `36aaaee` | um commit acima do cenário, nas mesmas branches |

Os commits ficam fixos no script, para que a medição não dependa de para onde as branches apontam.

## O que é reproduzido

| Arquivo em `resultados/` | Conteúdo | No artigo |
|---|---|---|
| `RESUMO.md` | todas as tabelas abaixo, lado a lado | — |
| `esforco.csv` | arquivos e linhas alterados da baseline para a versão modular | Tabela 1 |
| `<versão>/testes.csv` | testes por classe e total | Seções 5.2 e 5.8 |
| `<versão>/encapsulamento.*` | indicadores de encapsulamento (`EncapsulationReport`) | Tabela 2 |
| `<versão>/martin-metrics.*` | métricas de pacote de Martin (`MartinMetricsReport`) | Tabela 3 |
| `cenarios/<cenário>/arquivos.csv` | cada arquivo alterado no cenário, com linhas de código e contexto | Tabela 7 |
| `cenarios/aceite.csv` | cada critério de aceitação dos cenários, com o teste e o resultado em cada versão | Seções 4.4.4 e 5.7 |
| `<versão>/jdepend-2.9.1.*` | saída do JDepend 2.9.1 original e, classe a classe, se foi analisada e quais entradas `invokedynamic` tem (baseline e modular) | Seção 4.4.1 |
| `amostra-completude.*` | conferência de completude numa amostra com classes aninhadas, interna, local, anônima, record, enum com corpo e `package-info` (código em `reproducao/amostra-completude/`) | Seção 4.4.1 |
| `controle-sem-visita-inicial/` | as mesmas medições, na variante de controle | Tabela 8 |

`<versão>` é `baseline`, `modular` ou `controle-sem-visita-inicial`. O arquivo `reproducao/ambiente.txt` registra o SO, o JDK, o Gradle e as dependências da última execução (Quadro 2).

O SonarQube (Tabelas 5 e 6) fica de fora, porque precisa de um servidor e de um token.

## Pré-requisitos

- JDK 21
- Docker em execução (os testes de integração com MySQL e PostgreSQL usam Testcontainers)
- Python 3 e Git, com o histórico completo do repositório (um clone normal já traz)

## Como reproduzir

```bash
git checkout tcc-final
./reproducao/reproduzir.sh
git status --porcelain reproducao/resultados   # vazio = resultados idênticos aos do artigo
```

A execução leva de 10 a 20 minutos, porque roda a suíte de testes de sete versões. O workflow `.github/workflows/reproducao.yml` faz o mesmo no GitHub Actions (Ubuntu, JDK 21) e falha se algum resultado mudar.

## Observação sobre o Spring Modulith

A baseline e os commits derivados dela (base dos cenários e cenários) declaram o Spring Modulith 1.2.0, feito para o Spring Boot 3.3. Para que todas as versões usem a mesma linha, compatível com o Spring Boot 4.1, o script troca essa versão por 2.1.1 nos worktrees temporários. Nada é commitado nessas versões, e o código da aplicação não é alterado.
