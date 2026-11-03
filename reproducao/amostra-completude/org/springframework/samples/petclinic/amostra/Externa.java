package org.springframework.samples.petclinic.amostra;

import java.util.List;
import java.util.function.Supplier;

/**
 * Amostra para a conferência de completude do MartinMetricsReport (artigo, Seção 4.4.1). Reúne cada construção
 * que o compilador transforma em arquivo .class próprio (classe aninhada, interna, local e anônima, record, enum
 * com corpo de constante) e as que não geram arquivo (expressão lambda e referência a método, compiladas como
 * métodos privados desta classe). Não faz parte da aplicação: é compilada e medida por reproducao/reproduzir.sh.
 */
public class Externa {

	static class Aninhada {
	}

	class Interna {
	}

	record Registro(int x) {
	}

	enum Cor {

		AZUL {
			@Override
			String nome() {
				return "azul";
			}
		};

		String nome() {
			return name();
		}

	}

	Runnable anonima() {
		return new Runnable() {
			public void run() {
			}
		};
	}

	Object local() {
		class Local {
		}
		return new Local();
	}

	Supplier<String> lambda() {
		return () -> "x" + this;
	}

	List<String> referencia(List<Object> l) {
		return l.stream().map(String::valueOf).toList();
	}

}
