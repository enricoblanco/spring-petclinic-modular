package org.springframework.samples.petclinic.vet;

import java.text.ParseException;
import java.util.Locale;

import org.springframework.format.Formatter;
import org.springframework.stereotype.Component;

/**
 * Instructs Spring MVC on how to parse and print elements of type 'Vet', so a visit form can
 * pick its responsible vet by name.
 */
@Component
public class VetFormatter implements Formatter<Vet> {

	private final VetRepository vets;

	public VetFormatter(VetRepository vets) {
		this.vets = vets;
	}

	@Override
	public String print(Vet vet, Locale locale) {
		return vet.toString();
	}

	@Override
	public Vet parse(String text, Locale locale) throws ParseException {
		for (Vet vet : this.vets.findAll()) {
			if (print(vet, locale).equals(text)) {
				return vet;
			}
		}
		throw new ParseException("vet not found: " + text, 0);
	}

}
