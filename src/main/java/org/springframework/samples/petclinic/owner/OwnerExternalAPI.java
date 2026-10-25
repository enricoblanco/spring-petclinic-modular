package org.springframework.samples.petclinic.owner;

import java.util.List;

/**
 * Fachada pública (API do módulo) para comunicação entre outros módulos e o domínio Owner.
 */
public interface OwnerExternalAPI {

	PetDetails findPetDetails(int ownerId, int petId);

	List<Integer> findPetIds(int ownerId);

}
