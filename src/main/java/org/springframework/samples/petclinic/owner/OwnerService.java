package org.springframework.samples.petclinic.owner;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OwnerService implements OwnerExternalAPI {

	private final OwnerRepository owners;

	OwnerService(OwnerRepository owners) {
		this.owners = owners;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Integer> findPetIds(int ownerId) {
		// unknown owners are handled by OwnerController, callers just get no pets
		return this.owners.findById(ownerId)
			.map(owner -> owner.getPets().stream().map(Pet::getId).toList())
			.orElse(List.of());
	}

	@Override
	@Transactional(readOnly = true)
	public PetDetails findPetDetails(int ownerId, int petId) {
		Owner owner = this.owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException(
					"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));

		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException(
					"Pet with id " + petId + " not found for owner with id " + ownerId + ".");
		}

		String typeName = (pet.getType() != null) ? pet.getType().getName() : "";

		return new PetDetails(pet.getId(), pet.getName(), pet.getBirthDate(), typeName, owner.getFirstName(),
				owner.getLastName());
	}

}
