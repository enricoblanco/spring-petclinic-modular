#!/usr/bin/env bash
# Reproduz as medições do TCC: esforço da refatoração, testes, métricas de Martin, indicadores de
# encapsulamento, cenários de mudança e a variante de controle sem a visita inicial automática.
# Mede a versão modular (checkout atual) e as demais versões em worktrees temporários.
# Saída: reproducao/resultados/ (com RESUMO.md) e reproducao/ambiente.txt.
# O SonarQube fica de fora: precisa de servidor e token.
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
OUT="$ROOT/reproducao/resultados"

# Versões medidas (commits fixos, para que a medição não dependa de onde as branches apontam)
export BASELINE_REF="${BASELINE_REF:-acd6c2bdb27aee9358bfed046fe772e3949b94aa}"       # main
export CENARIO_BASE_REF="${CENARIO_BASE_REF:-49d9597fdaadd9a85600e30485376eb0d8d0585d}" # base modular dos cenários
export C1_BASELINE_REF="${C1_BASELINE_REF:-d1b961196bf4dd1e5a9cf006b8a1760459212f51}"   # cenario-c1-main
export C1_MODULAR_REF="${C1_MODULAR_REF:-63771a5478864b9a9c1a31f39324154d2ebbb931}"     # cenario-c1-modular
export C2_BASELINE_REF="${C2_BASELINE_REF:-34c36ba13a264b1ad0f67e967068ad8386b97305}"   # cenario-c2-main
export C2_MODULAR_REF="${C2_MODULAR_REF:-86eace813bea7beb7cd3d9a5b8ab8eaf788ce64d}"     # cenario-c2-modular
export CONTROLE_REF="${CONTROLE_REF:-a9481c32a88cc754a0af87bc940d910e161d02f3}"         # modular-sem-visita-inicial
# Commits de verificação: testes dos critérios de aceitação, um commit acima de cada cenário.
# Só têm a suíte executada; volume e alcance (Tabela 6) continuam medidos nos commits acima.
export C1_BASELINE_ACEITE_REF="${C1_BASELINE_ACEITE_REF:-eecef4003968fea780dad67c5ed61f6e86c1ce9b}"
export C1_MODULAR_ACEITE_REF="${C1_MODULAR_ACEITE_REF:-f679f73ce47ff5322cc9cc51f722d6397b025c17}"
export C2_BASELINE_ACEITE_REF="${C2_BASELINE_ACEITE_REF:-953d7896be28f0f4793ee35513f4a91cff0b2024}"
export C2_MODULAR_ACEITE_REF="${C2_MODULAR_ACEITE_REF:-36aaaee6ca5ead8457be7ce03bff08414dd1fe85}"

WT_PARENT="$(mktemp -d)"
export WT_PARENT
WORKTREES=()

cleanup() {
	for wt in "${WORKTREES[@]+"${WORKTREES[@]}"}"; do
		git -C "$ROOT" worktree remove --force "$wt" >/dev/null 2>&1 || true
	done
	rm -rf "$WT_PARENT"
}
trap cleanup EXIT

# Cria um worktree da versão e, se ela declarar o Spring Modulith 1.2.0 (feito para o Spring Boot 3.3),
# troca pela linha 2.1.x, compatível com o Spring Boot 4.1. Nada é commitado nessas versões.
prepare() {
	local name="$1" ref="$2" dir="$WT_PARENT/$1"
	git -C "$ROOT" worktree add --detach "$dir" "$ref" >/dev/null
	WORKTREES+=("$dir")
	perl -pi -e 's/(org\.springframework\.modulith:spring-modulith-[a-z-]+):1\.2\.0/$1:2.1.1/' "$dir/build.gradle"
}

run_tests() {
	(cd "$1" && ./gradlew --console=plain cleanTest test classes)
}

measure() {
	./gradlew --console=plain jdependReport encapsulationReport -PclassesDir="$2/build/classes/java/main" -Plabel="$1"
}

cd "$ROOT"
# Recria os resultados do zero, menos resultados/sonar/: evidência do SonarQube coletada à parte,
# que este script não reproduz.
mkdir -p "$OUT"
find "$OUT" -mindepth 1 -maxdepth 1 ! -name sonar -exec rm -rf {} +

echo "==> Versão modular (checkout atual)"
./gradlew --console=plain cleanTest test classes
measure modular "$ROOT"

echo "==> Baseline, variante de controle e cenários (worktrees)"
for spec in "baseline:$BASELINE_REF:tests" "controle:$CONTROLE_REF:tests" "cenario-base:$CENARIO_BASE_REF:classes" \
	"c1-baseline:$C1_BASELINE_REF:tests" "c1-modular:$C1_MODULAR_REF:tests" \
	"c2-baseline:$C2_BASELINE_REF:tests" "c2-modular:$C2_MODULAR_REF:tests"; do
	IFS=: read -r name ref kind <<<"$spec"
	echo "--> $name ($ref)"
	prepare "$name" "$ref"
	if [ "$kind" = tests ]; then
		run_tests "$WT_PARENT/$name"
	else
		(cd "$WT_PARENT/$name" && ./gradlew --console=plain classes)
	fi
	measure "$name" "$WT_PARENT/$name"
done

echo "==> JDepend 2.9.1 original na baseline e na versão modular (evidência da Seção 4.4.1)"
./gradlew --console=plain jdependOriginalReport -PclassesDir="$WT_PARENT/baseline/build/classes/java/main" -Plabel=baseline
./gradlew --console=plain jdependOriginalReport -PclassesDir="$ROOT/build/classes/java/main" -Plabel=modular

echo "==> Conferência de completude numa amostra com classes aninhadas, anônimas e package-info"
mkdir -p "$WT_PARENT/amostra-completude"
javac --release 21 -d "$WT_PARENT/amostra-completude" $(find "$ROOT/reproducao/amostra-completude" -name '*.java' | sort)
./gradlew --console=plain jdependReport -PclassesDir="$WT_PARENT/amostra-completude" -Plabel=amostra-completude

echo "==> Critérios de aceitação dos cenários (commits de verificação)"
for spec in "c1-baseline-aceite:$C1_BASELINE_ACEITE_REF" "c1-modular-aceite:$C1_MODULAR_ACEITE_REF" \
	"c2-baseline-aceite:$C2_BASELINE_ACEITE_REF" "c2-modular-aceite:$C2_MODULAR_ACEITE_REF"; do
	IFS=: read -r name ref <<<"$spec"
	echo "--> $name ($ref)"
	prepare "$name" "$ref"
	run_tests "$WT_PARENT/$name"
done

echo "==> Coletando resultados em $OUT"
python3 "$ROOT/reproducao/coletar.py" "$ROOT" "$WT_PARENT" "$OUT"

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
	echo "modular:                    $(git rev-parse HEAD) ($(git rev-parse --abbrev-ref HEAD))"
	echo "baseline:                   $BASELINE_REF"
	echo "controle sem visita inicial: $CONTROLE_REF"
	echo "base dos cenários:          $CENARIO_BASE_REF"
	echo "C1 baseline / modular:      $C1_BASELINE_REF / $C1_MODULAR_REF"
	echo "C2 baseline / modular:      $C2_BASELINE_REF / $C2_MODULAR_REF"
	echo "C1 aceite baseline / modular: $C1_BASELINE_ACEITE_REF / $C1_MODULAR_ACEITE_REF"
	echo "C2 aceite baseline / modular: $C2_BASELINE_ACEITE_REF / $C2_MODULAR_ACEITE_REF"
	echo "(versões que declaram o Spring Modulith 1.2.0 são medidas com o 2.1.1, ajustado no worktree)"
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
