package org.springframework.samples.petclinic.vet;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
class VetService implements VetExternalAPI {

	private final VetRepository vets;

	VetService(VetRepository vets) {
		this.vets = vets;
	}

	@Override
	public List<VetSummary> findAllVets() {
		return this.vets.findAll()
			.stream()
			.map(vet -> new VetSummary(vet.getId(), vet.getFirstName() + " " + vet.getLastName()))
			.toList();
	}

}
