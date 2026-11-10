# Evidência do SonarQube (Tabelas 4, 5 e 7 do artigo)

Respostas da API de um servidor SonarQube local, salvas em 10/11/2026 para registrar os números das Tabelas 4, 5 e 7 do artigo. **Esses valores não são reproduzidos por `reproducao/reproduzir.sh` nem pelo GitHub Actions.** O script preserva esta pasta, e o passo a passo para repetir a análise está em `reproducao/README.md`, seção *SonarQube: reprodução manual*.

- **Servidor:** SonarQube Community Build `26.10.0.133078`.
- **Scanner:** SonarScanner for Gradle `7.5.0.8588`.
- **Perfil Java:** *Sonar way core*, embutido e padrão (`isDefault: true`), com 274 regras ativas.
- **Projeto:** as três versões foram analisadas na mesma chave, `spring-petclinic-modular`.
- **Formato:** os JSON estão indentados, sem nenhuma outra alteração no conteúdo.

## Análises usadas

| Versão | Commit | Data da análise (UTC) | Tabelas |
|---|---|---|---|
| Baseline | `acd6c2b` | 25/10/2026 19:37:36 | 4 e 7 |
| Modular sem visita inicial (controle) | `a9481c3` | 03/11/2026 22:43:03 | 7 |
| Modular | `a56472f` | 03/11/2026 22:43:24 | 4, 5 e 7 |

O código de produção (`src/main`) do `a56472f` é idêntico ao da tag `tcc-final`.

## Arquivos

| Arquivo | Endpoint | Conteúdo |
|---|---|---|
| `analises.json` | `api/project_analyses/search?project=spring-petclinic-modular&ps=100` | todas as análises do projeto, com data e commit (`revision`) |
| `search_history.json` | `api/measures/search_history?component=spring-petclinic-modular&metrics=classes,ncloc,functions,complexity,cognitive_complexity,code_smells,duplicated_lines_density,sqale_index,sqale_rating&ps=1000` | totais do projeto em cada análise. É a fonte dos totais da baseline e do controle (Tabelas 4 e 7) |
| `modular-a56472f-component.json` | `api/measures/component?component=spring-petclinic-modular&metricKeys=` (mesmas métricas) | totais da última análise, a do `a56472f` (Tabela 4) |
| `modular-a56472f-component_tree-DIR.json` | `api/measures/component_tree?component=spring-petclinic-modular&metricKeys=classes,ncloc,complexity,cognitive_complexity&qualifiers=DIR&ps=100` | valores por diretório, ou seja, por módulo, da análise do `a56472f` (Tabela 5) |

## Limitações

- `api/measures/component` e `api/measures/component_tree` só devolvem a última análise do projeto, que é a do `a56472f`. Para a baseline e o controle, os totais vêm de `search_history.json`, e os valores por diretório não podem mais ser recuperados: o servidor guarda o histórico dos diretórios só com as datas, sem os valores. O artigo não usa valores por módulo dessas duas versões.
- A linha *petclinic (pacote raiz)* da Tabela 5 é a diferença entre o total e os módulos, como diz a nota da tabela.
- O servidor registra o commit em checkout, não o estado da árvore de trabalho. A análise de 24/10/2026 22:33, também marcada como `acd6c2b`, deu valores da versão modular (32 classes, 1.045 linhas de código), e provavelmente foi feita com alterações não commitadas. Por isso a baseline usa a análise de 25/10, que é igual à de 21/10 (`500158f`, mesmo `src/main` da `acd6c2b`).
