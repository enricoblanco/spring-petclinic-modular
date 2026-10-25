package org.springframework.samples.petclinic.visit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.samples.petclinic.owner.OwnerExternalAPI;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Contribui as visitas de cada Pet para as páginas do módulo Owner, sem que o Owner dependa do módulo Visit.
 */
// package referenced by name to keep the dependency one-way (visit -> owner)
@ControllerAdvice(basePackages = "org.springframework.samples.petclinic.owner")
class OwnerPageVisitsAdvice {

	private final VisitRepository visits;

	private final OwnerExternalAPI ownerApi;

	OwnerPageVisitsAdvice(VisitRepository visits, OwnerExternalAPI ownerApi) {
		this.visits = visits;
		this.ownerApi = ownerApi;
	}

	@ModelAttribute("visitsByPet")
	Map<Integer, List<Visit>> visitsByPet(@PathVariable(name = "ownerId", required = false) String ownerId) {
		Map<Integer, List<Visit>> visitsByPet = new LinkedHashMap<>();
		Integer id = parseOwnerId(ownerId);
		if (id == null) {
			return visitsByPet;
		}
		for (Integer petId : this.ownerApi.findPetIds(id)) {
			visitsByPet.put(petId, this.visits.findByPetId(petId));
		}
		return visitsByPet;
	}

	// invalid ids are left for OwnerController to reject
	private static Integer parseOwnerId(String ownerId) {
		if (ownerId == null) {
			return null;
		}
		try {
			return Integer.valueOf(ownerId);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
