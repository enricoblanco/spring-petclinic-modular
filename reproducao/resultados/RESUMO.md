# Resumo da reprodução

Gerado por `reproducao/reproduzir.sh`; não editar à mão. Baseline: commit `acd6c2b` (branch `main`); modular: tag `tcc-final` (branch `refatoracao-modular`). Os números do SonarQube (Tabelas 5 e 6) ficam fora da reprodução automática.

## Esforço da refatoração (artigo, Tabela 1)

| Escopo | Arquivos | Adicionados | Removidos | Renomeados | Modificados | Linhas inseridas | Linhas removidas |
|---|---|---|---|---|---|---|---|
| src/main | 26 | 9 | 1 | 1 | 15 | 360 | 176 |
| src/test | 15 | 4 | 0 | 4 | 7 | 361 | 158 |
| total | 41 | 13 | 1 | 5 | 22 | 721 | 334 |

## Testes automatizados (artigo, Seções 5.2 e 5.8)

| Versão | Testes | Falhas | Erros | Ignorados |
|---|---|---|---|---|
| Baseline | 82 | 0 | 0 | 0 |
| Modular | 87 | 0 | 0 | 0 |
| Modular sem a visita inicial (controle) | 85 | 0 | 0 | 0 |

## Encapsulamento e acoplamento entre módulos (artigo, Tabela 2)

| Indicador | Baseline | Modular | Variação |
|---|---|---|---|
| Tipos públicos / total | 18 / 25 (72,0%) | 9 / 33 (27,3%) | -44,7 p.p. |
| Entidades `@Entity` públicas | 6 / 6 (100,0%) | 0 / 6 (0,0%) | -100,0 p.p. |
| Repositórios públicos | 3 / 3 (100,0%) | 0 / 4 (0,0%) | -100,0 p.p. |
| Beans Spring públicos | 5 / 12 | 1 / 16 | -4 |
| Dependências classe → classe entre módulos | 10 | 13 | +3 |
| … que atingem tipos de implementação de outro módulo¹ | 1 | 0 | -1 |
| Classes fora de `visit` que dependem de `Visit` ou `VisitRepository` | 3 | 0 | -3 |
| Ciclos entre módulos | 0 | 0 | – |

¹ Referências estáticas detectadas no *bytecode*. Na versão modular, `PetClinicRuntimeHints` ainda referencia `Vet` pelo nome em texto, o que a análise estática não detecta.

## Métricas de pacote de Martin, antes → depois (artigo, Tabela 3)

| Pacote | Nc | Classes públicas | Ca | Ce | I | A (ArchUnit) | D (ArchUnit) | A (todas) | D (todas) |
|---|---|---|---|---|---|---|---|---|---|
| model | 3 → 3 | 3 → 3 | 3 → 4 | 0 → 0 | 0,00 → 0,00 | 0,00 → 0,00 | 1,00 → 1,00 | 0,00 → 0,00 | 1,00 → 1,00 |
| owner | 11 → 13 | 8 → 3 | 0 → 1 | 1 → 1 | 1,00 → 0,50 | 0,25 → 0,33 | 0,25 → 0,17 | 0,18 → 0,23 | 0,18 → 0,27 |
| petclinic | 2 → 2 | 2 → 2 | 0 → 0 | 2 → 1 | 1,00 → 1,00 | 0,00 → 0,00 | 0,00 → 0,00 | 0,00 → 0,00 | 0,00 → 0,00 |
| system | 4 → 4 | 1 → 1 | 0 → 0 | 0 → 0 | 1,00 → 1,00 | 0,00 → 0,00 | 0,00 → 0,00 | 0,00 → 0,00 | 0,00 → 0,00 |
| vet | 5 → 5 | 4 → 0 | 1 → 0 | 1 → 1 | 0,50 → 1,00 | 0,25 → 0,00 | 0,25 → 0,00 | 0,20 → 0,20 | 0,30 → 0,20 |
| visit (novo) | — → 6 | — → 0 | — → 0 | — → 2 | — → 1,00 | — → 0,00 | — → 0,00 | — → 0,17 | — → 0,17 |

Ca e Ce contam pacotes do projeto. A e D "ArchUnit" consideram só classes públicas; "todas" consideram todas as classes do pacote.

## Cenários de mudança (artigo, Tabela 7)

| Cenário | Versão | Arquivos alterados | Linhas de código (+/−) | Módulos de produção tocados | Arquivos existentes de outros contextos | Dependências de implementação | Testes (falhas) |
|---|---|---|---|---|---|---|---|
| C1 – 5 visitas mais recentes | Baseline | 5 | +62 / −2 | owner | 3 | 1 → 1 | 83 (0) |
| C1 – 5 visitas mais recentes | Modular | 3 | +19 / −1 | visit | 0 | 0 → 0 | 88 (0) |
| C2 – veterinário responsável | Baseline | 13 | +100 / −14 | owner, vet | 3 | 1 → 4 | 83 (0) |
| C2 – veterinário responsável | Modular | 15 | +121 / −15 | vet, visit | 1 | 0 → 0 | 88 (0) |

Diff de cada cenário em relação à sua base (`acd6c2b` na baseline, `49d9597` na modular). Linhas de código sem comentários e linhas em branco. "Outros contextos": arquivos já existentes, modificados, que não pertencem ao conceito de visita (nem SQL). A lista de arquivos de cada cenário está em `cenarios/<cenário>/arquivos.csv`.

## Influência da visita inicial automática (artigo, Tabela 8)

| Medida | Baseline | Modular sem a visita inicial | Modular | Efeito da funcionalidade |
|---|---|---|---|---|
| Testes aprovados | 82 | 85 | 87 | +2 |
| Arquivos alterados / linhas (vs. baseline) | — | 36 / +565 −331 | 41 / +721 −334 | +5 / +156 |
| Tipos (classes) | 25 | 31 | 33 | +2 |
| Tipos públicos / total | 18 / 25 (72,0%) | 8 / 31 (25,8%) | 9 / 33 (27,3%) | +1 |
| Entidades `@Entity` públicas | 6 / 6 (100,0%) | 0 / 6 (0,0%) | 0 / 6 (0,0%) | – |
| Repositórios públicos | 3 / 3 (100,0%) | 0 / 4 (0,0%) | 0 / 4 (0,0%) | – |
| Beans Spring públicos | 5 / 12 | 1 / 15 | 1 / 16 | – |
| Dependências classe → classe entre módulos | 10 | 12 | 13 | +1 |
| … que atingem tipos de implementação de outro módulo¹ | 1 | 0 | 0 | – |
| Classes fora de `visit` que dependem de `Visit` ou `VisitRepository` | 3 | 0 | 0 | – |
| Ciclos entre módulos | 0 | 0 | 0 | – |
| `owner`: Nc | 11 | 12 | 13 | — |
| `owner`: classes públicas | 8 | 2 | 3 | — |
| `owner`: D (ArchUnit) | 0,25 | 0,00 | 0,17 | — |
| `owner`: D (todas) | 0,18 | 0,25 | 0,27 | — |
| `visit`: Nc | — | 5 | 6 | — |
| `visit`: classes públicas | — | 0 | 0 | — |
| `visit`: D (ArchUnit) | — | 0,00 | 0,00 | — |
| `visit`: D (todas) | — | 0,20 | 0,17 | — |

Variante de controle: branch `modular-sem-visita-inicial`, igual à versão modular sem `PetRegisteredEvent`, `PetRegistrationListener` e seus testes.
