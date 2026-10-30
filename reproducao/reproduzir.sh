#!/usr/bin/env bash
# Reproduz as medições do TCC (testes, métricas de Martin e indicadores de encapsulamento)
# na versão modular (checkout atual) e na baseline (worktree temporário de BASELINE_REF).
# Saída: reproducao/resultados/<baseline|modular>/, reproducao/resultados/RESUMO.md e reproducao/ambiente.txt.
# O SonarQube fica de fora: precisa de servidor e token.
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
BASELINE_REF="${BASELINE_REF:-acd6c2b}"
OUT="$ROOT/reproducao/resultados"
WT_PARENT="$(mktemp -d)"
WT="$WT_PARENT/baseline"

cleanup() {
	git -C "$ROOT" worktree remove --force "$WT" >/dev/null 2>&1 || true
	rm -rf "$WT_PARENT"
}
trap cleanup EXIT

cd "$ROOT"
rm -rf "$OUT"
mkdir -p "$OUT/baseline" "$OUT/modular"

echo "==> Baseline: worktree de $BASELINE_REF"
git worktree add --detach "$WT" "$BASELINE_REF" >/dev/null
# A baseline declara o Spring Modulith 1.2.0, feito para o Spring Boot 3.3; usa-se a linha 2.1.x,
# compatível com o Spring Boot 4.1, como na versão modular. Nada é commitado na baseline.
perl -pi -e 's/(org\.springframework\.modulith:spring-modulith-[a-z-]+):1\.2\.0/$1:2.1.1/' "$WT/build.gradle"
(cd "$WT" && ./gradlew --console=plain cleanTest test classes)

echo "==> Modular: testes no checkout atual"
./gradlew --console=plain cleanTest test

echo "==> Métricas de Martin e indicadores de encapsulamento"
./gradlew --console=plain jdependReport encapsulationReport \
	-PclassesDir="$WT/build/classes/java/main" -Plabel=baseline
./gradlew --console=plain jdependReport encapsulationReport \
	-PclassesDir="$ROOT/build/classes/java/main" -Plabel=modular

echo "==> Coletando resultados em $OUT"
python3 - "$ROOT" "$WT" "$OUT" <<'PY'
import csv
import glob
import os
import shutil
import sys
import xml.etree.ElementTree as ET

root, wt, out = sys.argv[1:4]
builds = {"baseline": wt, "modular": root}

def test_summary(build_dir, dest):
    rows = []
    for path in sorted(glob.glob(os.path.join(build_dir, "build/test-results/test/*.xml"))):
        suite = ET.parse(path).getroot()
        rows.append([suite.get("name"), int(suite.get("tests")), int(suite.get("failures")),
                     int(suite.get("errors")), int(suite.get("skipped"))])
    total = ["TOTAL"] + [sum(r[i] for r in rows) for i in range(1, 5)]
    with open(dest, "w", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["classe", "testes", "falhas", "erros", "ignorados"])
        w.writerows(rows + [total])
    return total

def copy_report(src, dest, version):
    with open(src) as f:
        text = f.read()
    # absolute paths differ between machines: keep only the part relative to the measured version
    text = text.replace(wt, "<baseline>").replace(root, "<modular>")
    with open(dest, "w") as f:
        f.write(text)

totals = {}
for version, build_dir in builds.items():
    target = os.path.join(out, version)
    totals[version] = test_summary(build_dir, os.path.join(target, "testes.csv"))
    for ext in ("csv", "md", "txt"):
        copy_report(os.path.join(root, f"build/reports/martin-metrics/martin-metrics-{version}.{ext}"),
                    os.path.join(target, f"martin-metrics.{ext}"), version)
        copy_report(os.path.join(root, f"build/reports/encapsulation/encapsulation-{version}.{ext}"),
                    os.path.join(target, f"encapsulamento.{ext}"), version)

def read_csv(version, name):
    with open(os.path.join(out, version, name)) as f:
        return list(csv.DictReader(f))

def br(value):
    return value.replace(".", ",")

lines = ["# Resumo da reprodução", "",
         "Gerado por `reproducao/reproduzir.sh`; não editar à mão. "
         "Baseline: commit `acd6c2b` (branch `main`); modular: branch `refatoracao-modular`.", ""]

lines += ["## Testes automatizados (artigo, Seções 5.2 e 5.8)", "",
          "| Versão | Testes | Falhas | Erros | Ignorados |", "|---|---|---|---|---|"]
for version, label in (("baseline", "Baseline"), ("modular", "Modular")):
    t = totals[version]
    lines.append(f"| {label} | {t[1]} | {t[2]} | {t[3]} | {t[4]} |")
lines.append("")

enc = {v: {r["indicador"]: r for r in read_csv(v, "encapsulamento.csv")} for v in builds}
labels = [
    ("tipos_public", "Tipos públicos / total", True),
    ("entity_public", "Entidades `@Entity` públicas", True),
    ("repositorios_public", "Repositórios públicos", True),
    ("beans_public", "Beans Spring públicos", False),
    ("deps_entre_modulos", "Dependências classe → classe entre módulos", False),
    ("deps_em_implementacao", "… que atingem tipos de implementação de outro módulo", False),
    ("dependentes_de_visit_fora_de_visit", "Classes fora de `visit` que dependem de `Visit` ou `VisitRepository`", False),
    ("ciclos_entre_modulos", "Ciclos entre módulos", False),
]

def cell(row, pct):
    value, total = int(row["valor"]), row["total"]
    if not total:
        return str(value)
    text = f"{value} / {total}"
    if pct:
        text += f" ({br(f'{100 * value / int(total):.1f}')}%)"
    return text

def variation(b, m, pct):
    if pct:
        diff = 100 * int(m["valor"]) / int(m["total"]) - 100 * int(b["valor"]) / int(b["total"])
        return br(f"{diff:+.1f}") + " p.p."
    diff = int(m["valor"]) - int(b["valor"])
    return "–" if diff == 0 else f"{diff:+d}"

lines += ["## Encapsulamento e acoplamento entre módulos (artigo, Tabela 2)", "",
          "| Indicador | Baseline | Modular | Variação |", "|---|---|---|---|"]
for key, label, pct in labels:
    b, m = enc["baseline"][key], enc["modular"][key]
    lines.append(f"| {label} | {cell(b, pct)} | {cell(m, pct)} | {variation(b, m, pct)} |")
lines.append("")

martin = {v: {r["pacote"]: r for r in read_csv(v, "martin-metrics.csv")} for v in builds}
cols = [("nc", "Nc"), ("public", "Classes públicas"), ("ca", "Ca"), ("ce", "Ce"), ("i", "I"),
        ("a_archunit", "A (ArchUnit)"), ("d_archunit", "D (ArchUnit)"), ("a_todas", "A (todas)"),
        ("d_todas", "D (todas)")]
lines += ["## Métricas de pacote de Martin, antes → depois (artigo, Tabela 3)", "",
          "| Pacote | " + " | ".join(c[1] for c in cols) + " |", "|---" * (len(cols) + 1) + "|"]
for pkg in sorted(set(martin["baseline"]) | set(martin["modular"])):
    b, m = martin["baseline"].get(pkg), martin["modular"].get(pkg)
    name = pkg if b else f"{pkg} (novo)"
    values = []
    for key, _ in cols:
        before = br(b[key]) if b else "—"
        after = br(m[key]) if m else "—"
        values.append(f"{before} → {after}")
    lines.append(f"| {name} | " + " | ".join(values) + " |")
lines += ["", "Ca e Ce contam pacotes do projeto. A e D \"ArchUnit\" consideram só classes públicas; "
          "\"todas\" consideram todas as classes do pacote.", ""]

with open(os.path.join(out, "RESUMO.md"), "w") as f:
    f.write("\n".join(lines))
PY

echo "==> Registrando o ambiente em reproducao/ambiente.txt"
{
	echo "# Ambiente da última execução de reproducao/reproduzir.sh"
	echo
	echo "## Sistema operacional"
	uname -a
	if command -v sw_vers >/dev/null 2>&1; then sw_vers; fi
	echo
	echo "## Java"
	java -version 2>&1
	echo
	echo "## Gradle"
	./gradlew --version --console=plain | sed '/^$/d'
	echo
	echo "## Git e Docker"
	git --version
	docker --version 2>/dev/null || echo "docker: não encontrado"
	echo
	echo "## Commits medidos"
	echo "modular:  $(git rev-parse HEAD) ($(git rev-parse --abbrev-ref HEAD))"
	echo "baseline: $(git rev-parse "$BASELINE_REF") (Spring Modulith ajustado para 2.1.1 no worktree)"
	echo
	echo "## Dependências resolvidas (versão modular, testRuntimeClasspath)"
	./gradlew dependencies --configuration testRuntimeClasspath --console=plain \
		| grep -oE "(org\.springframework\.boot:spring-boot|org\.springframework:spring-core|org\.springframework\.modulith:spring-modulith-core|org\.hibernate\.orm:hibernate-core|com\.tngtech\.archunit:archunit|org\.junit\.jupiter:junit-jupiter-api):[^ ]+( -> [^ ]+)?" \
		| sort -u
	echo
	echo "## Dependência do instrumento de métricas (metricsRuntimeClasspath)"
	./gradlew dependencies --configuration metricsRuntimeClasspath --console=plain \
		| grep -oE "com\.tngtech\.archunit:archunit:[^ ]+" | sort -u
} > "$ROOT/reproducao/ambiente.txt"

echo "==> Concluído. Resultados em reproducao/resultados/ (resumo em RESUMO.md)."
