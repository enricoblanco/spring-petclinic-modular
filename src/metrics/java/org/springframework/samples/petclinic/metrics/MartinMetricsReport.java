package org.springframework.samples.petclinic.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.metrics.ArchitectureMetrics;
import com.tngtech.archunit.library.metrics.ComponentDependencyMetrics;
import com.tngtech.archunit.library.metrics.MetricsComponent;
import com.tngtech.archunit.library.metrics.MetricsComponents;

/**
 * Relatório das métricas de pacote de Robert C. Martin (Ca, Ce, I, A, D) usando o ArchUnit, que lê bytecode do
 * Java 21 (records, lambdas, method references) sem descartar classes, ao contrário do JDepend 2.9.1.
 * <p>
 * Ca, Ce, I e A/D "ArchUnit" vêm de {@link ArchitectureMetrics#componentDependencyMetrics}: Ca/Ce contam pacotes do
 * projeto e A considera apenas classes {@code public}. A/D "todas" usam A = abstratas / todas as classes do pacote
 * (régua do JDepend), para que mudanças de visibilidade não alterem a abstração medida.
 * <p>
 * Uso: {@code MartinMetricsReport <classesDir> <basePackage> <reportDir> <label>}
 */
public final class MartinMetricsReport {

	private MartinMetricsReport() {
	}

	public static void main(String[] args) throws IOException {
		Path classesDir = Path.of(args[0]);
		String basePackage = args[1];
		Path reportDir = Path.of(args[2]);
		String label = (args.length > 3 && !args[3].isBlank()) ? args[3] : "local";

		long classFilesOnDisk;
		try (Stream<Path> files = Files.walk(classesDir.resolve(basePackage.replace('.', '/')))) {
			classFilesOnDisk = files.filter(p -> p.toString().endsWith(".class")).count();
		}

		JavaClasses imported = new ClassFileImporter().importPath(classesDir);
		Map<String, List<JavaClass>> classesByPackage = new TreeMap<>();
		for (JavaClass javaClass : imported) {
			if (javaClass.getPackageName().equals(basePackage)
					|| javaClass.getPackageName().startsWith(basePackage + ".")) {
				classesByPackage.computeIfAbsent(javaClass.getPackageName(), k -> new ArrayList<>()).add(javaClass);
			}
		}
		int importedCount = classesByPackage.values().stream().mapToInt(List::size).sum();

		// one component per package with its direct classes only, so the root package does not swallow subpackages
		List<MetricsComponent<JavaClass>> components = new ArrayList<>();
		classesByPackage.forEach((pkg, classes) -> components.add(MetricsComponent.of(pkg, classes)));
		ComponentDependencyMetrics metrics = ArchitectureMetrics
			.componentDependencyMetrics(MetricsComponents.of(components));

		Map<String, Set<String>> efferent = new TreeMap<>();
		Map<String, Set<String>> afferent = new TreeMap<>();
		classesByPackage.keySet().forEach(pkg -> {
			efferent.put(pkg, new TreeSet<>());
			afferent.put(pkg, new TreeSet<>());
		});
		classesByPackage.forEach((pkg, classes) -> {
			for (JavaClass javaClass : classes) {
				for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
					String target = dependency.getTargetClass().getBaseComponentType().getPackageName();
					if (!target.equals(pkg) && classesByPackage.containsKey(target)) {
						efferent.get(pkg).add(target);
						afferent.get(target).add(pkg);
					}
				}
			}
		});

		StringBuilder out = new StringBuilder();
		line(out, "=".repeat(78));
		line(out, "Robert C. Martin package metrics (ArchUnit) - " + label);
		line(out, "Classes dir: " + classesDir.toAbsolutePath());
		line(out, "Arquivos .class no disco: " + classFilesOnDisk + " | Classes importadas: " + importedCount
				+ (classFilesOnDisk == importedCount ? "  (OK, nenhuma classe descartada)" : "  (DIVERGENCIA!)"));
		line(out, "=".repeat(78));

		List<Row> rows = new ArrayList<>();
		classesByPackage.forEach((pkg, classes) -> {
			classes.sort(Comparator.comparing(JavaClass::getName));
			List<JavaClass> abstractClasses = classes.stream().filter(MartinMetricsReport::isAbstract).toList();
			List<JavaClass> concreteClasses = classes.stream().filter(c -> !isAbstract(c)).toList();
			long publicClasses = classes.stream().filter(MartinMetricsReport::isPublic).count();

			double instability = metrics.getInstability(pkg);
			double abstractnessAll = (double) abstractClasses.size() / classes.size();
			Row row = new Row(shortName(pkg, basePackage), classes.size(), concreteClasses.size(),
					abstractClasses.size(), (int) publicClasses, metrics.getAfferentCoupling(pkg),
					metrics.getEfferentCoupling(pkg), instability, metrics.getAbstractness(pkg),
					metrics.getNormalizedDistanceFromMainSequence(pkg), abstractnessAll,
					Math.abs(abstractnessAll + instability - 1));
			rows.add(row);

			line(out, "");
			line(out, "Pacote: " + pkg + "  [" + row.name() + "]");
			line(out, "-".repeat(78));
			line(out, "  Total Classes (Nc):     " + row.nc() + "   (public: " + row.publicClasses() + ")");
			line(out, "  Concrete Classes:       " + row.concrete());
			line(out, "  Abstract Classes (Na):  " + row.na());
			line(out, "  Classes concretas:");
			concreteClasses.forEach(c -> line(out, "    - " + describe(c)));
			line(out, "  Classes abstratas/interfaces:");
			if (abstractClasses.isEmpty()) {
				line(out, "    (nenhuma)");
			}
			abstractClasses.forEach(c -> line(out, "    - " + describe(c)));
			line(out, "  Ca (Afferent Coupling): " + row.ca() + "  <- "
					+ shortNames(afferent.get(pkg), basePackage));
			line(out, "  Ce (Efferent Coupling): " + row.ce() + "  -> "
					+ shortNames(efferent.get(pkg), basePackage));
			line(out, "  I  (Instability):       " + fmt(row.i()));
			line(out, "  A  (Abstractness)  ArchUnit (so public): " + fmt(row.aArchUnit()) + " | todas as classes: "
					+ fmt(row.aAll()));
			line(out, "  D  (Distance)      ArchUnit (so public): " + fmt(row.dArchUnit()) + " | todas as classes: "
					+ fmt(row.dAll()));
		});

		String markdown = markdown(rows);
		String csv = csv(rows);
		line(out, "");
		line(out, "Resumo (Markdown) - " + label);
		out.append(markdown);
		line(out, "");
		line(out, "Resumo (CSV) - " + label);
		out.append(csv);

		System.out.print(out);

		Files.createDirectories(reportDir);
		String fileLabel = label.replaceAll("[^A-Za-z0-9._-]", "_");
		Files.writeString(reportDir.resolve("martin-metrics-" + fileLabel + ".txt"), out);
		Files.writeString(reportDir.resolve("martin-metrics-" + fileLabel + ".md"), markdown);
		Files.writeString(reportDir.resolve("martin-metrics-" + fileLabel + ".csv"), csv);
		System.out.println();
		System.out.println("Relatorio gravado em " + reportDir.toAbsolutePath());
	}

	private static boolean isAbstract(JavaClass javaClass) {
		return javaClass.isInterface() || javaClass.getModifiers().contains(JavaModifier.ABSTRACT);
	}

	private static boolean isPublic(JavaClass javaClass) {
		return javaClass.getModifiers().contains(JavaModifier.PUBLIC);
	}

	private static String describe(JavaClass javaClass) {
		List<String> tags = new ArrayList<>();
		tags.add(isPublic(javaClass) ? "public" : "package-private/outra");
		if (javaClass.isRecord()) {
			tags.add("record");
		}
		if (javaClass.isInterface()) {
			tags.add("interface");
		}
		else if (javaClass.getModifiers().contains(JavaModifier.ABSTRACT)) {
			tags.add("abstract");
		}
		if (javaClass.isEnum()) {
			tags.add("enum");
		}
		if (javaClass.isNestedClass()) {
			tags.add("nested");
		}
		String nameInPackage = javaClass.getName().substring(javaClass.getPackageName().length() + 1);
		return nameInPackage + " (" + String.join(", ", tags) + ")";
	}

	private static String shortName(String pkg, String basePackage) {
		return pkg.equals(basePackage) ? basePackage.substring(basePackage.lastIndexOf('.') + 1)
				: pkg.substring(basePackage.length() + 1);
	}

	private static String shortNames(Set<String> packages, String basePackage) {
		return packages.isEmpty() ? "(nenhum)"
				: packages.stream().map(p -> shortName(p, basePackage)).collect(Collectors.joining(", "));
	}

	private static String fmt(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static void line(StringBuilder out, String text) {
		out.append(text).append(System.lineSeparator());
	}

	private static String markdown(List<Row> rows) {
		StringBuilder md = new StringBuilder();
		line(md, "| Pacote | Nc | Concretas | Na | Public | Ca | Ce | I | A (ArchUnit) | D (ArchUnit) | A (todas) | D (todas) |");
		line(md, "|---|---|---|---|---|---|---|---|---|---|---|---|");
		for (Row r : rows) {
			line(md, "| " + r.name() + " | " + r.nc() + " | " + r.concrete() + " | " + r.na() + " | "
					+ r.publicClasses() + " | " + r.ca() + " | " + r.ce() + " | " + fmt(r.i()) + " | "
					+ fmt(r.aArchUnit()) + " | " + fmt(r.dArchUnit()) + " | " + fmt(r.aAll()) + " | " + fmt(r.dAll())
					+ " |");
		}
		return md.toString();
	}

	private static String csv(List<Row> rows) {
		StringBuilder csv = new StringBuilder();
		line(csv, "pacote,nc,concretas,na,public,ca,ce,i,a_archunit,d_archunit,a_todas,d_todas");
		for (Row r : rows) {
			line(csv, String.join(",", r.name(), String.valueOf(r.nc()), String.valueOf(r.concrete()),
					String.valueOf(r.na()), String.valueOf(r.publicClasses()), String.valueOf(r.ca()),
					String.valueOf(r.ce()), fmt(r.i()), fmt(r.aArchUnit()), fmt(r.dArchUnit()), fmt(r.aAll()),
					fmt(r.dAll())));
		}
		return csv.toString();
	}

	private record Row(String name, int nc, int concrete, int na, int publicClasses, int ca, int ce, double i,
			double aArchUnit, double dArchUnit, double aAll, double dAll) {
	}

}
