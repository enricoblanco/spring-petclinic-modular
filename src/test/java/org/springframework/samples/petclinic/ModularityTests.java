package org.springframework.samples.petclinic;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

	@Test
	void verifyModularity() {
		ApplicationModules modules = ApplicationModules.of(PetClinicApplication.class);
		modules.forEach(System.out::println);
		modules.verify();
	}
}
