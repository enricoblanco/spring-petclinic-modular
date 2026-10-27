package org.springframework.samples.petclinic.owner;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OwnerService implements OwnerExternalAPI {

	private final OwnerRepository owners;

	private final ApplicationEventPublisher events;

	OwnerService(OwnerRepository owners, ApplicationEventPublisher events) {
		this.owners = owners;
		this.events = events;
	}

	/**
	 * Adds the pet to the owner, saves it and publishes a {@link PetRegisteredEvent}.
	 */
	@Transactional
	void registerPet(Owner owner, Pet pet) {
		owner.addPet(pet);
		// saveAndFlush merges the owner: only the returned copy carries the generated pet id
		Owner saved = this.owners.saveAndFlush(owner);
		Pet savedPet = saved.getPet(pet.getName());
		Integer petId = (savedPet != null) ? savedPet.getId() : pet.getId();
		this.events.publishEvent(new PetRegisteredEvent(petId, saved.getId()));
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

		return new PetDetails(
			pet.getId(),
			pet.getName(),
			pet.getBirthDate(),
			typeName,
			owner.getFirstName(),
			owner.getLastName()
		);
	}

}
