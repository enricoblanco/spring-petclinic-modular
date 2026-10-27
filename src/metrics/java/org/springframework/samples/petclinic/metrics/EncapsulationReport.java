package org.springframework.samples.petclinic.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Indicadores de encapsulamento e acoplamento entre módulos, medidos no bytecode com o ArchUnit.
 * <p>
 * Módulo = subpacote direto do pacote-base (mesma regra do Spring Modulith); o pacote-base em si é "app". Só tipos de
 * topo são contados; dependências de classes aninhadas são atribuídas à classe de topo que as contém. Ca/Ce são
 * contados em classes (Martin, 1994): Ca = classes de fora que dependem do módulo, Ce = classes do módulo que dependem
 * de fora.
 * <p>
 * Uso: {@code EncapsulationReport <classesDir> <basePackage> <reportDir> <label>}
 */
public final class EncapsulationReport {

	private static final Set<String> BEAN_ANNOTATIONS = Set.of("org.springframework.stereotype.Component",
			"org.springframework.stereotype.Controller", "org.springframework.stereotype.Service",
			"org.springframework.stereotype.Repository", "org.springframework.context.annotation.Configuration",
			"org.springframework.web.bind.annotation.RestController",
			"org.springframework.web.bind.annotation.ControllerAdvice");

	private static final Set<String> VISIT_TYPES = Set.of("Visit", "VisitRepository");

	private EncapsulationReport() {
	}

	public static void main(String[] args) throws IOException {
		Path classesDir = Path.of(args[0]);
		String basePackage = args[1];
		Path reportDir = Path.of(args[2]);
		String label = (args.length > 3 && !args[3].isBlank()) ? args[3] : "local";

		JavaClasses imported = new ClassFileImporter().importPath(classesDir);
		List<JavaClass> all = new ArrayList<>();
		for (JavaClass javaClass : imported) {
			if (inBase(javaClass, basePackage)) {
				all.add(javaClass);
			}
		}
		List<JavaClass> types = all.stream()
			.filter(c -> c.isTopLevelClass() && !c.getSimpleName().equals("package-info"))
			.sorted((a, b) -> a.getName().compareTo(b.getName()))
			.toList();

		Map<String, ModuleStats> modules = new TreeMap<>();
		for (JavaClass type : types) {
			ModuleStats stats = modules.computeIfAbsent(module(type, basePackage), ModuleStats::new);
			stats.types.add(type.getSimpleName());
			if (isPublic(type)) {
				stats.publicTypes.add(type.getSimpleName());
			}
			count(type, EncapsulationReport::isEntity, stats.entities, stats.publicEntities);
			count(type, EncapsulationReport::isRepository, stats.repositories, stats.publicRepositories);
			count(type, EncapsulationReport::isBean, stats.beans, stats.publicBeans);
		}

		// distinct class-to-class edges crossing a module boundary, attributed to top-level classes
		Set<String> crossEdges = new TreeSet<>();
		Set<String> implementationEdges = new TreeSet<>();
		Set<String> visitDependents = new TreeSet<>();
		for (JavaClass origin : all) {
			JavaClass from = topLevel(origin);
			String fromModule = module(from, basePackage);
			for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
				JavaClass targetRaw = dependency.getTargetClass().getBaseComponentType();
				if (!inBase(targetRaw, basePackage)) {
					continue;
				}
				JavaClass to = topLevel(targetRaw);
				if (to.equals(from)) {
					continue;
				}
				String toModule = module(to, basePackage);
				if (VISIT_TYPES.contains(to.getSimpleName()) && !fromModule.equals("visit")
						&& !VISIT_TYPES.contains(from.getSimpleName())) {
					visitDependents.add(fromModule + "." + from.getSimpleName() + " -> " + to.getSimpleName());
				}
				if (fromModule.equals(toModule)) {
					continue;
				}
				String edge = fromModule + "." + from.getSimpleName() + " -> " + toModule + "." + to.getSimpleName();
				crossEdges.add(edge);
				modules.get(fromModule).efferentClasses.add(from.getSimpleName());
				modules.get(toModule).afferentClasses.add(fromModule + "." + from.getSimpleName());
				if (isEntity(to) || isRepository(to) || isController(to)) {
					implementationEdges.add(edge + "  [" + role(to) + "]");
				}
			}
		}

		EvaluationResult cycles = slices().matching(basePackage + ".(*)..")
			.should()
			.beFreeOfCycles()
			.allowEmptyShould(true)
			.evaluate(imported);
		List<String> cycleDetails = cycles.getFailureReport()
			.getDetails()
			.stream()
			.filter(d -> d.startsWith("Cycle detected"))
			.toList();

		int totalTypes = types.size();
		Totals t = new Totals(totalTypes, sum(modules, s -> s.publicTypes.size()), sum(modules, s -> s.entities.size()),
				sum(modules, s -> s.publicEntities.size()), sum(modules, s -> s.repositories.size()),
				sum(modules, s -> s.publicRepositories.size()), sum(modules, s -> s.beans.size()),
				sum(modules, s -> s.publicBeans.size()), crossEdges.size(), implementationEdges.size(),
				visitDependents.size(), cycleDetails.size());

		StringBuilder out = new StringBuilder();
		line(out, "=".repeat(78));
		line(out, "Indicadores de encapsulamento (ArchUnit) - " + label);
		line(out, "Classes dir: " + classesDir.toAbsolutePath());
		line(out, "Tipos de topo analisados: " + totalTypes + " (" + modules.size() + " modulos)");
		line(out, "=".repeat(78));
		modules.values().forEach(s -> {
			line(out, "");
			line(out, "Modulo: " + s.name);
			line(out, "-".repeat(78));
			line(out, "  Tipos:                 " + s.publicTypes.size() + " public de " + s.types.size() + "  "
					+ s.publicTypes);
			line(out, "  @Entity:               " + s.publicEntities.size() + " public de " + s.entities.size() + "  "
					+ s.entities);
			line(out, "  Repositorios:          " + s.publicRepositories.size() + " public de " + s.repositories.size()
					+ "  " + s.repositories);
			line(out, "  Beans Spring:          " + s.publicBeans.size() + " public de " + s.beans.size() + "  "
					+ s.beans);
			line(out, "  Ca (classes de fora):  " + s.afferentClasses.size() + "  " + s.afferentClasses);
			line(out, "  Ce (classes daqui):    " + s.efferentClasses.size() + "  " + s.efferentClasses);
		});
		section(out, "Dependencias entre modulos (pares classe -> classe distintos)", crossEdges);
		section(out, "Dependencias entre modulos que atingem tipos de implementacao", implementationEdges);
		section(out, "Classes fora do modulo visit que dependem de Visit/VisitRepository", visitDependents);
		section(out, "Ciclos entre modulos (ArchUnit slices)", cycleDetails);

		String markdown = markdown(label, t, modules);
		String csv = csv(t);
		line(out, "");
		out.append(markdown);
		line(out, "");
		out.append(csv);
		System.out.print(out);

		Files.createDirectories(reportDir);
		String fileLabel = label.replaceAll("[^A-Za-z0-9._-]", "_");
		Files.writeString(reportDir.resolve("encapsulation-" + fileLabel + ".txt"), out);
		Files.writeString(reportDir.resolve("encapsulation-" + fileLabel + ".md"), markdown);
		Files.writeString(reportDir.resolve("encapsulation-" + fileLabel + ".csv"), csv);
		System.out.println();
		System.out.println("Relatorio gravado em " + reportDir.toAbsolutePath());
	}

	private static void count(JavaClass type, Predicate<JavaClass> kind, Set<String> all, Set<String> exposed) {
		if (kind.test(type)) {
			all.add(type.getSimpleName());
			if (isPublic(type)) {
				exposed.add(type.getSimpleName());
			}
		}
	}

	private static boolean inBase(JavaClass javaClass, String basePackage) {
		String pkg = javaClass.getPackageName();
		return pkg.equals(basePackage) || pkg.startsWith(basePackage + ".");
	}

	private static String module(JavaClass javaClass, String basePackage) {
		String pkg = javaClass.getPackageName();
		if (pkg.equals(basePackage)) {
			return "app";
		}
		String rest = pkg.substring(basePackage.length() + 1);
		int dot = rest.indexOf('.');
		return dot < 0 ? rest : rest.substring(0, dot);
	}

	private static JavaClass topLevel(JavaClass javaClass) {
		JavaClass current = javaClass;
		while (current.getEnclosingClass().isPresent()) {
			current = current.getEnclosingClass().get();
		}
		return current;
	}

	private static boolean isPublic(JavaClass javaClass) {
		return javaClass.getModifiers().contains(JavaModifier.PUBLIC);
	}

	private static boolean isEntity(JavaClass javaClass) {
		return javaClass.isAnnotatedWith("jakarta.persistence.Entity");
	}

	// the Spring Data hierarchy is not on this classpath, so match the directly extended interfaces by package
	private static boolean isRepository(JavaClass javaClass) {
		return javaClass.isInterface() && javaClass.getRawInterfaces()
			.stream()
			.anyMatch(i -> i.getPackageName().startsWith("org.springframework.data."));
	}

	private static boolean isController(JavaClass javaClass) {
		return javaClass.isAnnotatedWith("org.springframework.stereotype.Controller")
				|| javaClass.isAnnotatedWith("org.springframework.web.bind.annotation.RestController");
	}

	private static boolean isBean(JavaClass javaClass) {
		return isRepository(javaClass) || javaClass.getAnnotations()
			.stream()
			.anyMatch(a -> BEAN_ANNOTATIONS.contains(a.getRawType().getName()));
	}

	private static String role(JavaClass javaClass) {
		return isEntity(javaClass) ? "@Entity" : isRepository(javaClass) ? "repositorio" : "controlador";
	}

	private static int sum(Map<String, ModuleStats> modules, java.util.function.ToIntFunction<ModuleStats> f) {
		return modules.values().stream().mapToInt(f).sum();
	}

	private static void section(StringBuilder out, String title, java.util.Collection<String> items) {
		line(out, "");
		line(out, title + ": " + items.size());
		line(out, "-".repeat(78));
		if (items.isEmpty()) {
			line(out, "  (nenhum)");
		}
		items.forEach(i -> line(out, "  " + i));
	}

	private static String pct(int part, int whole) {
		return whole == 0 ? "-" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * part / whole);
	}

	private static void line(StringBuilder out, String text) {
		out.append(text).append(System.lineSeparator());
	}

	private static String markdown(String label, Totals t, Map<String, ModuleStats> modules) {
		StringBuilder md = new StringBuilder();
		line(md, "Indicadores - " + label);
		line(md, "| Indicador | Valor |");
		line(md, "|---|---|");
		line(md, "| Tipos public / total | " + t.publicTypes() + " / " + t.types() + " ("
				+ pct(t.publicTypes(), t.types()) + ") |");
		line(md, "| @Entity public / total | " + t.publicEntities() + " / " + t.entities() + " |");
		line(md, "| Repositorios public / total | " + t.publicRepositories() + " / " + t.repositories() + " |");
		line(md, "| Beans Spring public / total | " + t.publicBeans() + " / " + t.beans() + " |");
		line(md, "| Dependencias classe->classe entre modulos | " + t.crossEdges() + " |");
		line(md, "| ...que atingem tipos de implementacao | " + t.implementationEdges() + " |");
		line(md, "| Classes fora de visit dependentes de Visit/VisitRepository | " + t.visitDependents() + " |");
		line(md, "| Ciclos entre modulos | " + t.cycles() + " |");
		line(md, "");
		line(md, "| Modulo | Tipos | Public | @Entity public | Repos public | Beans public | Ca (classes) | Ce (classes) |");
		line(md, "|---|---|---|---|---|---|---|---|");
		modules.values()
			.forEach(s -> line(md, "| " + s.name + " | " + s.types.size() + " | " + s.publicTypes.size() + " | "
					+ s.publicEntities.size() + "/" + s.entities.size() + " | " + s.publicRepositories.size() + "/"
					+ s.repositories.size() + " | " + s.publicBeans.size() + "/" + s.beans.size() + " | "
					+ s.afferentClasses.size() + " | " + s.efferentClasses.size() + " |"));
		return md.toString();
	}

	private static String csv(Totals t) {
		StringBuilder csv = new StringBuilder();
		line(csv, "indicador,valor,total");
		line(csv, "tipos_public," + t.publicTypes() + "," + t.types());
		line(csv, "entity_public," + t.publicEntities() + "," + t.entities());
		line(csv, "repositorios_public," + t.publicRepositories() + "," + t.repositories());
		line(csv, "beans_public," + t.publicBeans() + "," + t.beans());
		line(csv, "deps_entre_modulos," + t.crossEdges() + ",");
		line(csv, "deps_em_implementacao," + t.implementationEdges() + ",");
		line(csv, "dependentes_de_visit_fora_de_visit," + t.visitDependents() + ",");
		line(csv, "ciclos_entre_modulos," + t.cycles() + ",");
		return csv.toString();
	}

	private static final class ModuleStats {

		private final String name;

		private final Set<String> types = new TreeSet<>();

		private final Set<String> publicTypes = new TreeSet<>();

		private final Set<String> entities = new TreeSet<>();

		private final Set<String> publicEntities = new TreeSet<>();

		private final Set<String> repositories = new TreeSet<>();

		private final Set<String> publicRepositories = new TreeSet<>();

		private final Set<String> beans = new TreeSet<>();

		private final Set<String> publicBeans = new TreeSet<>();

		private final Set<String> afferentClasses = new TreeSet<>();

		private final Set<String> efferentClasses = new TreeSet<>();

		private ModuleStats(String name) {
			this.name = name;
		}

	}

	private record Totals(int types, int publicTypes, int entities, int publicEntities, int repositories,
			int publicRepositories, int beans, int publicBeans, int crossEdges, int implementationEdges,
			int visitDependents, int cycles) {
	}

}
