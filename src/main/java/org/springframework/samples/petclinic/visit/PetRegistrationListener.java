package org.springframework.samples.petclinic.visit;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.samples.petclinic.owner.PetRegisteredEvent;
import org.springframework.stereotype.Component;

/**
 * Subscriber do módulo Visit: agenda uma consulta inicial para cada Pet recém-cadastrado.
 */
@Component
class PetRegistrationListener {

	static final String INITIAL_VISIT_DESCRIPTION = "Initial checkup";

	private final VisitRepository visits;

	PetRegistrationListener(VisitRepository visits) {
		this.visits = visits;
	}

	@ApplicationModuleListener
	void on(PetRegisteredEvent event) {
		// Visit defaults its date to tomorrow
		Visit visit = new Visit();
		visit.setPetId(event.petId());
		visit.setDescription(INITIAL_VISIT_DESCRIPTION);
		this.visits.save(visit);
	}

}
