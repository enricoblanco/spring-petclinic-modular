"""Reúne as saídas de reproducao/reproduzir.sh em reproducao/resultados e gera o RESUMO.md.

Uso: coletar.py <raiz-do-repositório> <pasta-dos-worktrees> <pasta-de-saída>
Tudo o que é gravado é determinístico (sem datas, tempos ou caminhos absolutos), para que duas
execuções em máquinas diferentes produzam arquivos idênticos.
"""

import csv
import glob
import os
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT, WT, OUT = sys.argv[1:4]
REFS = {k: os.environ[k] for k in ("BASELINE_REF", "CENARIO_BASE_REF", "C1_BASELINE_REF", "C1_MODULAR_REF",
                                   "C2_BASELINE_REF", "C2_MODULAR_REF", "CONTROLE_REF")}
REFS["MODULAR_REF"] = "HEAD"

# label used by the gradle reports -> (output folder, build directory, has tests)
VERSIONS = {
    "baseline": ("baseline", os.path.join(WT, "baseline"), True),
    "modular": ("modular", ROOT, True),
    "controle": ("controle-sem-visita-inicial", os.path.join(WT, "controle"), True),
    "cenario-base": ("cenarios/base-modular", os.path.join(WT, "cenario-base"), False),
    "c1-baseline": ("cenarios/c1-baseline", os.path.join(WT, "c1-baseline"), True),
    "c1-modular": ("cenarios/c1-modular", os.path.join(WT, "c1-modular"), True),
    "c2-baseline": ("cenarios/c2-baseline", os.path.join(WT, "c2-baseline"), True),
    "c2-modular": ("cenarios/c2-modular", os.path.join(WT, "c2-modular"), True),
}
SCENARIOS = [  # (cenário, versão, label, base, ref)
    ("C1 – 5 visitas mais recentes", "Baseline", "c1-baseline", "BASELINE_REF", "C1_BASELINE_REF"),
    ("C1 – 5 visitas mais recentes", "Modular", "c1-modular", "CENARIO_BASE_REF", "C1_MODULAR_REF"),
    ("C2 – veterinário responsável", "Baseline", "c2-baseline", "BASELINE_REF", "C2_BASELINE_REF"),
    ("C2 – veterinário responsável", "Modular", "c2-modular", "CENARIO_BASE_REF", "C2_MODULAR_REF"),
]


def git(*args):
    return subprocess.run(["git", "-C", ROOT, *args], check=True, capture_output=True, text=True).stdout


def write_csv(path, header, rows):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(header)
        w.writerows(rows)


def read_csv(path):
    with open(path) as f:
        return list(csv.DictReader(f))


def br(value):
    return str(value).replace(".", ",")


# ---------------------------------------------------------------- tests and reports per version

def test_summary(build_dir, dest):
    rows = []
    for path in sorted(glob.glob(os.path.join(build_dir, "build/test-results/test/TEST-*.xml"))):
        # Gradle never puts spaces in these names; "X 2.xml" copies come from file sync tools (iCloud)
        if " " in os.path.basename(path):
            continue
        suite = ET.parse(path).getroot()
        rows.append([suite.get("name"), int(suite.get("tests")), int(suite.get("failures")),
                     int(suite.get("errors")), int(suite.get("skipped"))])
    total = ["TOTAL"] + [sum(r[i] for r in rows) for i in range(1, 5)]
    write_csv(dest, ["classe", "testes", "falhas", "erros", "ignorados"], rows + [total])
    return total


def copy_report(src, dest):
    with open(src) as f:
        text = f.read()
    # absolute paths differ between machines: keep only the name of the measured version
    for label, (_, build_dir, _) in VERSIONS.items():
        if build_dir != ROOT:
            text = text.replace(build_dir, f"<{label}>")
    text = text.replace(ROOT, "<modular>")
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "w") as f:
        f.write(text)


tests = {}
for label, (folder, build_dir, has_tests) in VERSIONS.items():
    target = os.path.join(OUT, folder)
    os.makedirs(target, exist_ok=True)
    if has_tests:
        tests[label] = test_summary(build_dir, os.path.join(target, "testes.csv"))
    for ext in ("csv", "md", "txt"):
        copy_report(os.path.join(ROOT, f"build/reports/martin-metrics/martin-metrics-{label}.{ext}"),
                    os.path.join(target, f"martin-metrics.{ext}"))
        copy_report(os.path.join(ROOT, f"build/reports/encapsulation/encapsulation-{label}.{ext}"),
                    os.path.join(target, f"encapsulamento.{ext}"))

enc = {label: {r["indicador"]: r for r in read_csv(os.path.join(OUT, folder, "encapsulamento.csv"))}
       for label, (folder, _, _) in VERSIONS.items()}
martin = {label: {r["pacote"]: r for r in read_csv(os.path.join(OUT, folder, "martin-metrics.csv"))}
          for label, (folder, _, _) in VERSIONS.items()}


# ---------------------------------------------------------------- diffs (Tables 1, 7 and 8)

def numstat(base, ref, *paths):
    """[(insertions, deletions, path)] with rename detection, as in `git diff --stat`."""
    rows = []
    for line in git("diff", "-M", "--numstat", base, ref, "--", *paths).splitlines():
        ins, dels, path = line.split("\t", 2)
        rows.append((int(ins), int(dels), path))
    return rows


def name_status(base, ref, *paths):
    rows = []
    for line in git("diff", "-M", "--name-status", base, ref, "--", *paths).splitlines():
        parts = line.split("\t")
        rows.append((parts[0][0], parts[-1]))
    return rows


def code_lines(base, ref):
    """Inserted and removed lines per file, ignoring blank lines and Java comments."""
    stats, current, in_block = {}, None, False
    for line in git("diff", "-M", "-U0", base, ref).splitlines():
        if line.startswith("+++ "):
            current = line[6:] if line[4:6] == "b/" else None
            in_block = False
            if current:
                stats.setdefault(current, [0, 0])
            continue
        if line.startswith(("--- ", "@@", "diff ", "index ", "new file", "deleted file", "similarity", "rename ")):
            continue
        if not current or line[:1] not in "+-":
            continue
        text = line[1:].strip()
        if current.endswith(".java"):
            if in_block:
                in_block = "*/" not in text
                continue
            if text.startswith("/*"):
                in_block = "*/" not in text
                continue
            if text == "" or text.startswith("//") or text.startswith("*"):
                continue
        elif text == "":
            continue
        stats[current][0 if line[0] == "+" else 1] += 1
    return stats


def context(path):
    if path.endswith(".sql"):
        return "sql"
    if "/visit/" in path or "Visit" in os.path.basename(path):
        return "visita"
    return "outro"


# Table 1: refactoring effort (baseline -> modular)
effort = []
for scope in ("src/main", "src/test"):
    status = [s for s, _ in name_status(REFS["BASELINE_REF"], "HEAD", scope)]
    lines = numstat(REFS["BASELINE_REF"], "HEAD", scope)
    effort.append([scope, len(lines), status.count("A"), status.count("D"), status.count("R"), status.count("M"),
                   sum(r[0] for r in lines), sum(r[1] for r in lines)])
effort.append(["total"] + [sum(r[i] for r in effort) for i in range(1, 8)])
write_csv(os.path.join(OUT, "esforco.csv"),
          ["escopo", "arquivos", "adicionados", "removidos", "renomeados", "modificados", "linhas_inseridas",
           "linhas_removidas"], effort)

# Table 7: change scenarios
scenario_rows = []
for name, version, label, base_key, ref_key in SCENARIOS:
    base, ref = REFS[base_key], REFS[ref_key]
    status = dict((path, s) for s, path in name_status(base, ref))
    lines = code_lines(base, ref)
    files = [(status[p], p, lines.get(p, [0, 0])[0], lines.get(p, [0, 0])[1], context(p)) for p in sorted(status)]
    write_csv(os.path.join(OUT, VERSIONS[label][0], "arquivos.csv"),
              ["status", "arquivo", "linhas_codigo_inseridas", "linhas_codigo_removidas", "contexto"], files)
    modules = sorted({p.split("/petclinic/")[1].split("/")[0] for _, p, _, _, _ in files
                      if p.startswith("src/main/java/") and "/petclinic/" in p and p.count("/") > 7})
    other = [p for s, p, _, _, c in files if s == "M" and c == "outro"]
    base_label = "baseline" if base_key == "BASELINE_REF" else "cenario-base"
    scenario_rows.append([name, version, len(files), sum(f[2] for f in files), sum(f[3] for f in files),
                          ", ".join(modules), len(other), enc[base_label]["deps_em_implementacao"]["valor"],
                          enc[label]["deps_em_implementacao"]["valor"], tests[label][1], tests[label][2] + tests[label][3]])


# ---------------------------------------------------------------- RESUMO.md

def total_row(label):
    t = tests[label]
    return t[1], t[2], t[3], t[4]


def ratio(row, pct):
    value, total = int(row["valor"]), row["total"]
    if not total:
        return str(value)
    return f"{value} / {total}" + (f" ({br(f'{100 * value / int(total):.1f}')}%)" if pct else "")


def variation(b, m, pct):
    if pct:
        diff = 100 * int(m["valor"]) / int(m["total"]) - 100 * int(b["valor"]) / int(b["total"])
        return br(f"{diff:+.1f}") + " p.p."
    diff = int(m["valor"]) - int(b["valor"])
    return "–" if diff == 0 else f"{diff:+d}"


ENC_LABELS = [
    ("tipos_public", "Tipos públicos / total", True),
    ("entity_public", "Entidades `@Entity` públicas", True),
    ("repositorios_public", "Repositórios públicos", True),
    ("beans_public", "Beans Spring públicos", False),
    ("deps_entre_modulos", "Dependências classe → classe entre módulos", False),
    ("deps_em_implementacao", "… que atingem tipos de implementação de outro módulo¹", False),
    ("dependentes_de_visit_fora_de_visit", "Classes fora de `visit` que dependem de `Visit` ou `VisitRepository`", False),
    ("ciclos_entre_modulos", "Ciclos entre módulos", False),
]
MARTIN_COLS = [("nc", "Nc"), ("public", "Classes públicas"), ("ca", "Ca"), ("ce", "Ce"), ("i", "I"),
               ("a_archunit", "A (ArchUnit)"), ("d_archunit", "D (ArchUnit)"), ("a_todas", "A (todas)"),
               ("d_todas", "D (todas)")]

L = ["# Resumo da reprodução", "",
     "Gerado por `reproducao/reproduzir.sh`; não editar à mão. Baseline: commit `acd6c2b` (branch `main`); "
     "modular: tag `tcc-final` (branch `refatoracao-modular`). Os números do SonarQube (Tabelas 5 e 6) "
     "ficam fora da reprodução automática.", ""]

L += ["## Esforço da refatoração (artigo, Tabela 1)", "",
      "| Escopo | Arquivos | Adicionados | Removidos | Renomeados | Modificados | Linhas inseridas | Linhas removidas |",
      "|---|---|---|---|---|---|---|---|"]
for row in effort:
    L.append("| " + " | ".join(str(v) for v in row) + " |")
L.append("")

L += ["## Testes automatizados (artigo, Seções 5.2 e 5.8)", "",
      "| Versão | Testes | Falhas | Erros | Ignorados |", "|---|---|---|---|---|"]
for label, title in (("baseline", "Baseline"), ("modular", "Modular"),
                     ("controle", "Modular sem a visita inicial (controle)")):
    L.append(f"| {title} | " + " | ".join(str(v) for v in total_row(label)) + " |")
L.append("")

L += ["## Encapsulamento e acoplamento entre módulos (artigo, Tabela 2)", "",
      "| Indicador | Baseline | Modular | Variação |", "|---|---|---|---|"]
for key, label, pct in ENC_LABELS:
    b, m = enc["baseline"][key], enc["modular"][key]
    L.append(f"| {label} | {ratio(b, pct)} | {ratio(m, pct)} | {variation(b, m, pct)} |")
L += ["", "¹ Referências estáticas detectadas no *bytecode*. Na versão modular, `PetClinicRuntimeHints` ainda "
      "referencia `Vet` pelo nome em texto, o que a análise estática não detecta.", ""]

L += ["## Métricas de pacote de Martin, antes → depois (artigo, Tabela 3)", "",
      "| Pacote | " + " | ".join(c[1] for c in MARTIN_COLS) + " |", "|---" * (len(MARTIN_COLS) + 1) + "|"]
for pkg in sorted(set(martin["baseline"]) | set(martin["modular"])):
    b, m = martin["baseline"].get(pkg), martin["modular"].get(pkg)
    cells = [f"{br(b[k]) if b else '—'} → {br(m[k]) if m else '—'}" for k, _ in MARTIN_COLS]
    L.append(f"| {pkg if b else pkg + ' (novo)'} | " + " | ".join(cells) + " |")
L += ["", "Ca e Ce contam pacotes do projeto. A e D \"ArchUnit\" consideram só classes públicas; "
      "\"todas\" consideram todas as classes do pacote.", ""]

L += ["## Cenários de mudança (artigo, Tabela 7)", "",
      "| Cenário | Versão | Arquivos alterados | Linhas de código (+/−) | Módulos de produção tocados | "
      "Arquivos existentes de outros contextos | Dependências de implementação | Testes (falhas) |",
      "|---|---|---|---|---|---|---|---|"]
for name, version, files, ins, dels, modules, other, dep_before, dep_after, n_tests, n_fail in scenario_rows:
    L.append(f"| {name} | {version} | {files} | +{ins} / −{dels} | {modules} | {other} | "
             f"{dep_before} → {dep_after} | {n_tests} ({n_fail}) |")
L += ["", "Diff de cada cenário em relação à sua base (`acd6c2b` na baseline, `49d9597` na modular). Linhas de "
      "código sem comentários e linhas em branco. \"Outros contextos\": arquivos já existentes, modificados, "
      "que não pertencem ao conceito de visita (nem SQL). A lista de arquivos de cada cenário está em "
      "`cenarios/<cenário>/arquivos.csv`.", ""]

ACCEPTANCE = [  # (cenário, critério, teste na baseline, teste na modular); None: não se aplica
    ("C1", "Cinco visitas mais recentes, em ordem decrescente de data",
     "VisitRepositoryTests.shouldFindOnlyTheMostRecentVisitsNewestFirst",
     "VisitRepositoryTests.shouldFindOnlyTheMostRecentVisitsNewestFirst"),
    ("C1", "Empate de data: a visita cadastrada por último (maior id) vem primeiro",
     "VisitRepositoryTests.shouldBreakDateTiesByNewestVisitFirst",
     "VisitRepositoryTests.shouldBreakDateTiesByNewestVisitFirst"),
    ("C1", "Animal sem visitas: lista vazia, sem erro",
     "VisitRepositoryTests.shouldFindNoRecentVisitsForPetWithoutVisits",
     "VisitRepositoryTests.shouldFindNoRecentVisitsForPetWithoutVisits"),
    ("C2", "Veterinário válido: formulário aceito e redirecionado",
     "VisitControllerTests.processNewVisitFormSuccess", "VisitControllerTests.processNewVisitFormSuccess"),
    ("C2", "Veterinário ausente: formulário com erro no campo, nada gravado",
     "VisitControllerTests.processNewVisitFormHasErrorsWhenVetIsMissing",
     "VisitControllerTests.processNewVisitFormHasErrorsWhenVetIsMissing"),
    ("C2", "Identificador em formato inválido: formulário com erro no campo, nada gravado",
     None, "VisitControllerTests.processNewVisitFormHasErrorsWhenVetIdIsInvalid"),
    ("C2", "Veterinário inexistente: formulário com erro no campo, nada gravado",
     "VisitControllerTests.processNewVisitFormHasErrorsWhenVetDoesNotExist",
     "VisitControllerTests.processNewVisitFormHasErrorsWhenVetDoesNotExist"),
    ("C2", "Banco: vet_id inexistente rejeitado pela chave estrangeira",
     "VisitVetIntegrityTests.shouldRejectVisitReferencingMissingVet",
     "VisitVetIntegrityTests.shouldRejectVisitReferencingMissingVet"),
    ("C2", "Banco: vet_id existente ou nulo aceito",
     "VisitVetIntegrityTests.shouldAcceptVisitReferencingExistingVetOrNoVet",
     "VisitVetIntegrityTests.shouldAcceptVisitReferencingExistingVetOrNoVet"),
]


def test_results(label):
    """{"Classe.método": "aprovado" | "falhou" | "ignorado"} from the JUnit XML of a worktree."""
    results = {}
    for path in glob.glob(os.path.join(WT, label, "build/test-results/test/TEST-*.xml")):
        for case in ET.parse(path).getroot().iter("testcase"):
            key = case.get("classname").rsplit(".", 1)[-1] + "." + case.get("name").removesuffix("()")
            failed = case.find("failure") is not None or case.find("error") is not None
            results[key] = "falhou" if failed else "ignorado" if case.find("skipped") is not None else "aprovado"
    return results


accept_tests = {}
for scenario in ("c1", "c2"):
    for version in ("baseline", "modular"):
        label = f"{scenario}-{version}-aceite"
        accept_tests[label] = test_summary(os.path.join(WT, label), os.path.join(OUT, "cenarios", label, "testes.csv"))
results = {label: test_results(label) for label in accept_tests}


def outcome(scenario, version, test):
    return "não se aplica" if test is None else results[f"{scenario.lower()}-{version}-aceite"].get(test, "ausente")


accept_rows = [[sc, crit, tb or "", outcome(sc, "baseline", tb), tm or "", outcome(sc, "modular", tm)]
               for sc, crit, tb, tm in ACCEPTANCE]
write_csv(os.path.join(OUT, "cenarios", "aceite.csv"),
          ["cenario", "criterio", "teste_baseline", "resultado_baseline", "teste_modular", "resultado_modular"],
          accept_rows)
L += ["## Critérios de aceitação dos cenários (artigo, Seções 4.4.4 e 5.7)", "",
      "| Cenário | Critério | Baseline | Modular |", "|---|---|---|---|"]
for sc, crit, tb, rb, tm, rm in accept_rows:
    L.append(f"| {sc} | {crit} | {f'`{tb}` ({rb})' if tb else rb} | {f'`{tm}` ({rm})' if tm else rm} |")
L += ["", "Testes executados nos commits de verificação, um acima de cada cenário. Suítes completas: "
      + "; ".join(f"{label} {t[1]} testes, {t[2] + t[3]} falhas" for label, t in accept_tests.items())
      + ". Na baseline do C2 o veterinário é identificado pelo nome, e um texto que não corresponde a nenhum "
      "veterinário é o caso \"inexistente\".", ""]

ctrl, mod, base = enc["controle"], enc["modular"], enc["baseline"]
mc, mm, mb = martin["controle"], martin["modular"], martin["baseline"]
diff_ctrl = numstat(REFS["BASELINE_REF"], REFS["CONTROLE_REF"], "src/main", "src/test")
diff_mod = numstat(REFS["BASELINE_REF"], "HEAD", "src/main", "src/test")


def eff(rows):
    return f"{len(rows)} / +{sum(r[0] for r in rows)} −{sum(r[1] for r in rows)}"


def delta(key):
    return variation(ctrl[key], mod[key], False)


L += ["## Influência da visita inicial automática (artigo, Tabela 8)", "",
      "| Medida | Baseline | Modular sem a visita inicial | Modular | Efeito da funcionalidade |",
      "|---|---|---|---|---|",
      f"| Testes aprovados | {tests['baseline'][1]} | {tests['controle'][1]} | {tests['modular'][1]} | "
      f"{tests['modular'][1] - tests['controle'][1]:+d} |",
      f"| Arquivos alterados / linhas (vs. baseline) | — | {eff(diff_ctrl)} | {eff(diff_mod)} | "
      f"{len(diff_mod) - len(diff_ctrl):+d} / +{sum(r[0] for r in diff_mod) - sum(r[0] for r in diff_ctrl)} |",
      f"| Tipos (classes) | {base['tipos_public']['total']} | {ctrl['tipos_public']['total']} | "
      f"{mod['tipos_public']['total']} | {int(mod['tipos_public']['total']) - int(ctrl['tipos_public']['total']):+d} |"]
for key, label, pct in ENC_LABELS:
    L.append(f"| {label} | {ratio(base[key], pct)} | {ratio(ctrl[key], pct)} | {ratio(mod[key], pct)} | "
             f"{delta(key)} |")
for pkg in ("owner", "visit"):
    for k, title in (("nc", "Nc"), ("public", "classes públicas"), ("d_archunit", "D (ArchUnit)"),
                     ("d_todas", "D (todas)")):
        b = br(mb[pkg][k]) if pkg in mb else "—"
        L.append(f"| `{pkg}`: {title} | {b} | {br(mc[pkg][k])} | {br(mm[pkg][k])} | — |")
L += ["", "Variante de controle: branch `modular-sem-visita-inicial`, igual à versão modular sem "
      "`PetRegisteredEvent`, `PetRegistrationListener` e seus testes.", ""]

with open(os.path.join(OUT, "RESUMO.md"), "w") as f:
    f.write("\n".join(L))
