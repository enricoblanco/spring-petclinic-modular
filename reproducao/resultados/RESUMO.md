# Resumo da reprodução

Gerado por `reproducao/reproduzir.sh`; não editar à mão. Baseline: commit `acd6c2b` (branch `main`); modular: branch `refatoracao-modular`.

## Testes automatizados (artigo, Seções 5.2 e 5.8)

| Versão | Testes | Falhas | Erros | Ignorados |
|---|---|---|---|---|
| Baseline | 82 | 0 | 0 | 0 |
| Modular | 87 | 0 | 0 | 0 |

## Encapsulamento e acoplamento entre módulos (artigo, Tabela 2)

| Indicador | Baseline | Modular | Variação |
|---|---|---|---|
| Tipos públicos / total | 18 / 25 (72,0%) | 9 / 33 (27,3%) | -44,7 p.p. |
| Entidades `@Entity` públicas | 6 / 6 (100,0%) | 0 / 6 (0,0%) | -100,0 p.p. |
| Repositórios públicos | 3 / 3 (100,0%) | 0 / 4 (0,0%) | -100,0 p.p. |
| Beans Spring públicos | 5 / 12 | 1 / 16 | -4 |
| Dependências classe → classe entre módulos | 10 | 13 | +3 |
| … que atingem tipos de implementação de outro módulo | 1 | 0 | -1 |
| Classes fora de `visit` que dependem de `Visit` ou `VisitRepository` | 3 | 0 | -3 |
| Ciclos entre módulos | 0 | 0 | – |

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
