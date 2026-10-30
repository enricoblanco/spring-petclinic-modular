# TCC: monólito tradicional × monólito modular (Spring PetClinic)

[![Reprodução do TCC](https://github.com/enricoblanco/spring-petclinic-modular/actions/workflows/reproducao.yml/badge.svg?branch=refatoracao-modular)](https://github.com/enricoblanco/spring-petclinic-modular/actions/workflows/reproducao.yml)

Código e medições do TCC *Análise de manutenibilidade em arquiteturas de software: um estudo comparativo entre monólito tradicional e monólito modular no ecossistema Java* (Enrico Cidade Blanco, orientação do Prof. Dr. Elvandi da Silva Júnior, UNISINOS, 2026).

O estudo compara duas versões do Spring PetClinic:

| Versão | Onde está | O que é |
|---|---|---|
| Baseline (monólito tradicional) | branch `main`, commit `acd6c2b` | código original + instrumentação de medição |
| Modular (monólito modular) | branch `refatoracao-modular`, tag [`tcc-final`](https://github.com/enricoblanco/spring-petclinic-modular/tree/tcc-final) | refatoração com Spring Modulith: módulos `owner`, `vet`, `visit`, `system` e `model` |

Os resultados já gerados estão em [`reproducao/resultados/RESUMO.md`](reproducao/resultados/RESUMO.md). A cada push na `refatoracao-modular`, o workflow [Reprodução do TCC](https://github.com/enricoblanco/spring-petclinic-modular/actions/workflows/reproducao.yml) refaz todas as medições em uma máquina limpa e confere se os números são os mesmos. Abra a última execução e veja a aba **Summary**.

## 1. Pré-requisitos

| Ferramenta | Versão | Para quê |
|---|---|---|
| JDK | 21 | compilar e rodar o projeto (o Gradle vem junto, via `./gradlew`) |
| Docker | qualquer versão recente, **em execução** | testes de integração com MySQL e PostgreSQL (Testcontainers) |
| Git | 2.x | clonar e criar o worktree da baseline |
| Python | 3.x | montar os arquivos de resultado |

Confira com `java -version`, `docker info`, `git --version` e `python3 --version`. A primeira execução baixa as dependências do Gradle e as imagens do MySQL e do PostgreSQL, então precisa de internet.

## 2. Reproduzir tudo com um comando

```bash
git clone https://github.com/enricoblanco/spring-petclinic-modular.git
cd spring-petclinic-modular
git checkout tcc-final
./reproducao/reproduzir.sh
```

O script leva alguns minutos e faz, em ordem:

1. cria um worktree temporário da baseline (`acd6c2b`) e roda a suíte de testes dela;
2. roda a suíte de testes da versão modular;
3. gera as métricas de Martin e os indicadores de encapsulamento das duas versões;
4. grava tudo em `reproducao/resultados/` e o ambiente usado em `reproducao/ambiente.txt`;
5. remove o worktree temporário.

Para conferir se os números são iguais aos do artigo:

```bash
git status --porcelain reproducao/resultados
```

Se não aparecer nada, os resultados são idênticos aos versionados. Se aparecer algum arquivo, `git diff reproducao/resultados` mostra o que mudou.

### Onde está cada resultado

| Arquivo | Conteúdo | No artigo |
|---|---|---|
| `reproducao/resultados/RESUMO.md` | as três tabelas abaixo, lado a lado | — |
| `reproducao/resultados/<versão>/testes.csv` | testes por classe e total | Seções 5.2 e 5.8 |
| `reproducao/resultados/<versão>/encapsulamento.{csv,md,txt}` | tipos, entidades, repositórios e beans públicos; dependências entre módulos; ciclos | Tabela 2 |
| `reproducao/resultados/<versão>/martin-metrics.{csv,md,txt}` | Nc, Na, Ca, Ce, I, A e D por pacote | Tabela 3 |
| `reproducao/ambiente.txt` | SO, JDK, Gradle e versões das dependências | Quadro 2 |

`<versão>` é `baseline` ou `modular`. Os arquivos `.txt` trazem os detalhes que justificam cada número, como a lista de classes de cada pacote e cada dependência entre módulos.

## 3. Rodar cada medição separadamente

Todos os comandos abaixo rodam na raiz do repositório, com a tag `tcc-final` (ou a branch `refatoracao-modular`) em checkout.

```bash
# Testes da versão modular (inclui o ModularityTests, que roda o verify() do Spring Modulith)
./gradlew test

# Só a verificação de modularidade do Spring Modulith (Tabela 4)
./gradlew test --tests '*ModularityTests'

# Métricas de Martin (Tabela 3) -> build/reports/martin-metrics/
./gradlew jdependReport

# Indicadores de encapsulamento (Tabela 2) -> build/reports/encapsulation/
./gradlew encapsulationReport
```

Para medir a **baseline** com os mesmos instrumentos, compile-a num worktree e aponte os relatórios para as classes dela:

```bash
git worktree add /tmp/baseline acd6c2b
(cd /tmp/baseline && ./gradlew classes)
./gradlew jdependReport encapsulationReport -PclassesDir=/tmp/baseline/build/classes/java/main -Plabel=baseline
git worktree remove --force /tmp/baseline
```

Os relatórios saem com o sufixo `-baseline` nas mesmas pastas. Os instrumentos ficam em `src/metrics/java/.../metrics/` (`MartinMetricsReport` e `EncapsulationReport`).

## 4. SonarQube (opcional, fora da reprodução automática)

As Tabelas 5 e 6 do artigo vêm do SonarQube Community Build, que precisa de um servidor. Para repetir:

```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:community
```

1. Abra http://localhost:9000, entre com `admin`/`admin` e troque a senha.
2. Crie um projeto local com a chave `spring-petclinic-modular` e gere um token de análise.
3. Rode a análise. O token é lido da variável de ambiente e nunca fica em arquivo:

```bash
SONAR_TOKEN=<seu-token> ./gradlew sonar
```

As métricas aparecem no painel do projeto (aba *Measures*). Para a baseline, faça o checkout de `acd6c2b`. O `build.gradle` desse commit traz um token antigo escrito no arquivo, que não deve ser usado: troque o valor de `sonar.token` pelo seu token.

## 5. Cenários de mudança (Tabela 7)

Os dois cenários foram implementados nas duas versões, cada um em uma branch:

| Cenário | Baseline | Modular |
|---|---|---|
| C1: página do dono mostra só as 5 visitas mais recentes | `cenario-c1-main` | `cenario-c1-modular` |
| C2: veterinário responsável pela visita | `cenario-c2-main` | `cenario-c2-modular` |

O custo de cada implementação é o diff em relação à sua base:

```bash
git fetch origin 'refs/heads/cenario-*:refs/heads/cenario-*'
git diff --stat acd6c2b cenario-c1-main
git diff --stat 49d9597 cenario-c1-modular
git diff --stat acd6c2b cenario-c2-main
git diff --stat 49d9597 cenario-c2-modular
```

O commit `49d9597` é a versão modular usada como base dos cenários. Ela tem o mesmo código de produção da tag `tcc-final`.

## 6. Observações

- **Versões:** Spring Boot 4.1.0, Spring Framework 7.0.8, Spring Modulith 2.1.1, ArchUnit 1.4.2, Hibernate ORM 7.4.1 e JUnit Jupiter 6.0.3. As versões efetivas da última execução ficam em `reproducao/ambiente.txt`.
- **Baseline e Spring Modulith:** o commit da baseline declara o Spring Modulith 1.2.0, feito para o Spring Boot 3.3. O script troca para 2.1.1 só no worktree temporário, sem alterar o código da aplicação nem commitar nada na `main`.
- **Por que ArchUnit e não JDepend:** o JDepend 2.9.1 não lê bytecode do Java 21 e descarta classes com lambdas, referências a métodos e records. A task se chama `jdependReport` por histórico, mas usa o ArchUnit.

---

# Spring PetClinic Sample Application [![Build Status](https://github.com/spring-projects/spring-petclinic/actions/workflows/maven-build.yml/badge.svg)](https://github.com/spring-projects/spring-petclinic/actions/workflows/maven-build.yml)[![Build Status](https://github.com/spring-projects/spring-petclinic/actions/workflows/gradle-build.yml/badge.svg)](https://github.com/spring-projects/spring-petclinic/actions/workflows/gradle-build.yml)

[![Open in Gitpod](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#https://github.com/spring-projects/spring-petclinic) [![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://github.com/codespaces/new?hide_repo_select=true&ref=main&repo=7517918)

## Understanding the Spring Petclinic application with a few diagrams

See the presentation here:  
[Spring Petclinic Sample Application (legacy slides)](https://speakerdeck.com/michaelisvy/spring-petclinic-sample-application?slide=20)

> **Note:** These slides refer to a legacy, pre–Spring Boot version of Petclinic and may not reflect the current Spring Boot–based implementation.  
> For up-to-date information, please refer to this repository and its documentation.


## Run Petclinic locally

Spring Petclinic is a [Spring Boot](https://spring.io/guides/gs/spring-boot) application built using [Maven](https://spring.io/guides/gs/maven/) or [Gradle](https://spring.io/guides/gs/gradle/).
Java 17 or later is required for the build, and the application can run with Java 17 or newer.

You first need to clone the project locally:

```bash
git clone https://github.com/spring-projects/spring-petclinic.git
cd spring-petclinic
```
If you are using Maven, you can start the application on the command-line as follows:

```bash
./mvnw spring-boot:run
```
With Gradle, the command is as follows:

```bash
./gradlew bootRun
```

You can then access the Petclinic at <http://localhost:8080/>.

<img width="1042" alt="petclinic-screenshot" src="https://cloud.githubusercontent.com/assets/838318/19727082/2aee6d6c-9b8e-11e6-81fe-e889a5ddfded.png">

You can, of course, run Petclinic in your favorite IDE.
See below for more details.

## Building a Container

There is no `Dockerfile` in this project. You can build a container image (if you have a docker daemon) using the Spring Boot build plugin:

```bash
./mvnw spring-boot:build-image
```

## Running the Container Image

```bash
docker images | grep petclinic
docker run -p 8080:8080 docker.io/library/spring-petclinic:latest
```

## In case you find a bug/suggested improvement for Spring Petclinic

Our issue tracker is available [here](https://github.com/spring-projects/spring-petclinic/issues).

## Database configuration

In its default configuration, Petclinic uses an in-memory database (H2) which
gets populated at startup with data. The h2 console is exposed at `http://localhost:8080/h2-console`,
and it is possible to inspect the content of the database using the `jdbc:h2:mem:<uuid>` URL. The UUID is printed at startup to the console.

A similar setup is provided for MySQL and PostgreSQL if a persistent database configuration is needed. Note that whenever the database type changes, the app needs to run with a different profile: `spring.profiles.active=mysql` for MySQL or `spring.profiles.active=postgres` for PostgreSQL. See the [Spring Boot documentation](https://docs.spring.io/spring-boot/how-to/properties-and-configuration.html#howto.properties-and-configuration.set-active-spring-profiles) for more detail on how to set the active profile.

You can start MySQL or PostgreSQL locally with whatever installer works for your OS or use docker:

```bash
docker run -e MYSQL_USER=petclinic -e MYSQL_PASSWORD=petclinic -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=petclinic -p 3306:3306 mysql:9.7
```

or

```bash
docker run -e POSTGRES_USER=petclinic -e POSTGRES_PASSWORD=petclinic -e POSTGRES_DB=petclinic -p 5432:5432 postgres:18.4
```

Further documentation is provided for [MySQL](https://github.com/spring-projects/spring-petclinic/blob/main/src/main/resources/db/mysql/petclinic_db_setup_mysql.txt)
and [PostgreSQL](https://github.com/spring-projects/spring-petclinic/blob/main/src/main/resources/db/postgres/petclinic_db_setup_postgres.txt).

Instead of vanilla `docker` you can also use the provided `docker-compose.yml` file to start the database containers. Each one has a service named after the Spring profile:

```bash
docker compose up mysql
```

or

```bash
docker compose up postgres
```

## Test Applications

At development time we recommend you use the test applications set up as `main()` methods in `PetClinicIntegrationTests` (using the default H2 database and also adding Spring Boot Devtools), `MySqlTestApplication` and `PostgresIntegrationTests`. These are set up so that you can run the apps in your IDE to get fast feedback and also run the same classes as integration tests against the respective database. The MySql integration tests use Testcontainers to start the database in a Docker container, and the Postgres tests use Docker Compose to do the same thing.

## Compiling the CSS

There is a `petclinic.css` in `src/main/resources/static/resources/css`. It was generated from the `petclinic.scss` source, combined with the [Bootstrap](https://getbootstrap.com/) library. If you make changes to the `scss`, or upgrade Bootstrap, you will need to re-compile the CSS resources using the Maven profile "css", i.e. `./mvnw package -P css`. There is no build profile for Gradle to compile the CSS.

## Working with Petclinic in your IDE

### Prerequisites

The following items should be installed in your system:

- Java 17 or newer (full JDK, not a JRE)
- [Git command line tool](https://help.github.com/articles/set-up-git)
- Your preferred IDE
  - Eclipse with the m2e plugin. Note: when m2e is available, there is a m2 icon in `Help -> About` dialog. If m2e is
  not there, follow the installation process [here](https://www.eclipse.org/m2e/)
  - [Spring Tools Suite](https://spring.io/tools) (STS)
  - [IntelliJ IDEA](https://www.jetbrains.com/idea/)
  - [VS Code](https://code.visualstudio.com)

### Steps

1. On the command line run:

    ```bash
    git clone https://github.com/spring-projects/spring-petclinic.git
    ```

1. Inside Eclipse or STS:

    Open the project via `File -> Import -> Maven -> Existing Maven project`, then select the root directory of the cloned repo.

    Then either build on the command line `./mvnw generate-resources` or use the Eclipse launcher (right-click on project and `Run As -> Maven install`) to generate the CSS. Run the application's main method by right-clicking on it and choosing `Run As -> Java Application`.

1. Inside IntelliJ IDEA:

    In the main menu, choose `File -> Open` and select the Petclinic [pom.xml](pom.xml). Click on the `Open` button.

    - CSS files are generated from the Maven build. You can build them on the command line `./mvnw generate-resources` or right-click on the `spring-petclinic` project then `Maven -> Generates sources and Update Folders`.

    - A run configuration named `PetClinicApplication` should have been created for you if you're using a recent Ultimate version. Otherwise, run the application by right-clicking on the `PetClinicApplication` main class and choosing `Run 'PetClinicApplication'`.

1. Navigate to the Petclinic

    Visit [http://localhost:8080](http://localhost:8080) in your browser.

## Looking for something in particular?

|Spring Boot Configuration | Class or Java property files  |
|--------------------------|---|
|The Main Class | [PetClinicApplication](https://github.com/spring-projects/spring-petclinic/blob/main/src/main/java/org/springframework/samples/petclinic/PetClinicApplication.java) |
|Properties Files | [application.properties](https://github.com/spring-projects/spring-petclinic/blob/main/src/main/resources) |
|Caching | [CacheConfiguration](https://github.com/spring-projects/spring-petclinic/blob/main/src/main/java/org/springframework/samples/petclinic/system/CacheConfiguration.java) |

## Interesting Spring Petclinic branches and forks

The Spring Petclinic "main" branch in the [spring-projects](https://github.com/spring-projects/spring-petclinic)
GitHub org is the "canonical" implementation based on Spring Boot and Thymeleaf. There are
[quite a few forks](https://spring-petclinic.github.io/docs/forks.html) in the GitHub org
[spring-petclinic](https://github.com/spring-petclinic). If you are interested in using a different technology stack to implement the Pet Clinic, please join the community there.

## Interaction with other open-source projects

One of the best parts about working on the Spring Petclinic application is that we have the opportunity to work in direct contact with many Open Source projects. We found bugs/suggested improvements on various topics such as Spring, Spring Data, Bean Validation and even Eclipse! In many cases, they've been fixed/implemented in just a few days.
Here is a list of them:

| Name | Issue |
|------|-------|
| Spring JDBC: simplify usage of NamedParameterJdbcTemplate | [SPR-10256](https://github.com/spring-projects/spring-framework/issues/14889) and [SPR-10257](https://github.com/spring-projects/spring-framework/issues/14890) |
| Bean Validation / Hibernate Validator: simplify Maven dependencies and backward compatibility |[HV-790](https://hibernate.atlassian.net/browse/HV-790) and [HV-792](https://hibernate.atlassian.net/browse/HV-792) |
| Spring Data: provide more flexibility when working with JPQL queries | [DATAJPA-292](https://github.com/spring-projects/spring-data-jpa/issues/704) |

## Contributing

The [issue tracker](https://github.com/spring-projects/spring-petclinic/issues) is the preferred channel for bug reports, feature requests and submitting pull requests.

For pull requests, editor preferences are available in the [editor config](.editorconfig) for easy use in common text editors. Read more and download plugins at <https://editorconfig.org>. All commits must include a __Signed-off-by__ trailer at the end of each commit message to indicate that the contributor agrees to the Developer Certificate of Origin.
For additional details, please refer to the blog post [Hello DCO, Goodbye CLA: Simplifying Contributions to Spring](https://spring.io/blog/2025/01/06/hello-dco-goodbye-cla-simplifying-contributions-to-spring).

## License

The Spring PetClinic sample application is released under version 2.0 of the [Apache License](https://www.apache.org/licenses/LICENSE-2.0).
